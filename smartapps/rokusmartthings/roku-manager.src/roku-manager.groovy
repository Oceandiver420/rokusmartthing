/**
 *  Roku Manager
 *
 *  Copyright 2018 Steven Feldman
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * This code was originaly based on github.com/MadMouse/SmartThings but has been almost completely rewritten.
 *
 * Notes/Issues/TODOSs:
 * - roku page may not refresh as fast as it should
 */

definition(
  name: "Roku Manager",
  namespace: "RokuSmartThings",
  author: "Steven Feldman",
  description: "Manage Roku Devices",
  singleInstance: true,
  category: "SmartThings Labs",
  iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
  iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
  iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
  page(name: "deviceDiscoveryPage", nextPage: "createDHPage")
  page(name: "createDHPage")
}

def deviceDiscoveryPage() {
  log.debug "deviceDiscoveryPage"
  int refreshCount = !state.refreshCount ? 0 : state.refreshCount as int
  state.refreshCount = refreshCount + 1

  if (!state.subscribe) {
    log.debug "subscribe :: ${refreshCount}"
    ssdpSubscribe();
  }
  
  // ssdp request every 25 seconds
  if ((refreshCount % 5) == 0) {
    ssdpDiscover()
  }
  
  return dynamicPage(name: "deviceDiscoveryPage", title: "Discovery Started!",
                     nextPage: "createDHPage", refreshInterval: 5,
                     install: false, uninstall: true) {
    section("Please wait while we discover your Roku Devices. " +
            "Discovery can take five minutes or more, so sit back and relax! " +
            "To create device handlers for one or more rokus, " +
            "specify the name of the device, and then hit next.") {
      def devices = getRokuDevices().values().each {
        def id = rokuKey(it)
        input(
          name: id, type: "bool", title: "Roku (${it.host})",
          required: true, defaultValue: settings[id])
      }
    }
  }
}

def createDHPage() {
  log.debug "createDHPage"
  updateDeviceSelection()

  return dynamicPage(name: "createDHPage", title:"Create Roku Device Handlers",
                     uninstall: false, install: true) {
    def found = false
    getRokuDevices().values().each { d ->
      if (!d.enabled) { return }
      found = true
      section("To create a button for 'Roku (${d.host})', just give it a name.") {
        d.deviceHandlers.each() { k, v ->
          def id = dhNameKey(d, k)
          input(
            name: id, type: "text", title: "${v.label} Suggested: 'Roku ${v.label}'",
            required: false, defaultValue: settings[id])
        }
      }
    }
    if (!found) {
      section("Please enable at least one roku device.") {}
    }
  }
}

def installed() {
  log.debug "Installed with settings: ${settings}"
  initialize()
}

def updated() {
  log.debug "Updated with settings: ${settings}"
  initialize()
}

def initialize() {
  unsubscribe()
  unschedule()
  state.subscribe = false
  updateDeviceSelection() // processes preferences
  updateDeviceHandlers() // create device handlers
  runEvery5Minutes("ssdpDiscover")  // ensures that if the ip changes, the handlers still works
}

def uninstalled() {
  getChildDevices().each {
    deleteChildDevice(it.deviceNetworkId)
  }
  state.rokuDevices = [:]
}


/***
  updateDeviceSelection iterates through the roku devices and update its state
  based on the current preferences.
***/
def updateDeviceSelection() {
  getRokuDevices().values().each { d ->
    d.enabled = settings[rokuKey(d)]
    if (d.enabled) {
      setAvailableDeviceHandlers(d)
    }
  }
}

/***
  updateDeviceHandlers iterates through the roku devices and adds/removes device
  handlers based on the current preferences. If a roku device is disabled, then
  all of its device handlers are removed.
***/
def updateDeviceHandlers() {
  log.debug "updateDeviceHandlers"
  
  getRokuDevices().values().each { d ->
    if (!d.enabled) {
      d.deviceHandlers.keys().each {
        deleteChildDevice(it)
      }
      return
    }

    d.deviceHandlers.each {nid, dh ->
      def child = getChildDevice(nid)
      if (!dh.deviceName && child) {
        // no name means it was disabled.
        log.debug "removing device handler: $nid"
        deleteChildDevice(nid)
      } else if (dh.deviceName && !child) {
        // Create handler.
        log.debug "Creating ${dh.deviceName} (mac: ${d.mac}, host: ${d.host})"

        // Special case the general roku remote device handler
        if (nid == d.mac) {
          addChildDevice("RokuSmartThings", "Roku", nid, d.hub,
            ["label": "${dh.deviceName}", "data": ["mac": d.mac, "host": d.host]])
        } else {
          addChildDevice("RokuSmartThings", "Roku Button Tile", nid, d.hub,
            ["label": "${dh.deviceName}",
             "data": ["type": dh.type, "action": dh.action, "host": d.host]])
        }
      }
    }    
  }
}


/***
  setAvailableDeviceHandlers - generates a list of available roku buttons.
  If there was an enabled button that is no longer availble (e.g app was removed),
  it will remain in the list.
***/
def setAvailableDeviceHandlers(d) {
  // dhs stores the map of networkID -> deviceHandler objects that are used
  // to create the preference page and devicehandlers.
  def dhs = [:]

  // Special case the general remote device handler.
  dhs[d.mac] = [
    label: "Remote",
    deviceName: null
  ]

  // Special case wake on lan command.
  dhs["WakeViaLan"] = [
    label: "Wake Via Lan"
    deviceName: null
    type: null
    action: d.mac
  ]
  
  // Add buttons for keys on the remote.
  remoteKeys().each() {
    def id = it.replaceAll(' ', '')
    dhs[id] = [
      label: it,
      type: "key",
      deviceName: null,
      action: id
    ]
  }

  // Add buttons for roku apps.
  d.apps.each() { k, v ->
    dhs[k] = [
      label: v,
      type: "app",
      deviceName: null,
      action: k
    ]
  }

  // Copy over dhs that are enabled but not in the current activity list.
  def cur_dhs = d.deviceHandlers
  cur_dhs?.each() { k, v ->
    if (v.deviceName) { dhs[k] = v }
  }

  // Set the enabled state based on the preferences
  dhs.each() { k, v ->
    v.deviceName = settings[dhNameKey(d, k)]
  }

  // update the button list.
  d.deviceHandlers = dhs
}

/***
  ssdpHandler is called when an ssdp event is triggered.
  It creates a new roku device if one does not exist and update the host
  (i.e ip:port) of an existing roku device.
  It will then an async query to get a list of roku apps for this device.
  These will appear in the user preferences.
**/
def ssdpHandler(evt) {
  def newDevice = parseLanMessage(evt.description)
  def host = makeHostAddress(newDevice.networkAddress, newDevice.deviceAddress)

  def devices = getRokuDevices()
  def curDevice = devices.get(newDevice.mac, null)
  
  if (!curDevice) {
    log.debug "Adding new network device: (ssdpUSN ${ssdpUSN}: ${newDevice})"
    newDevice << ["name": newDevice.name ?: "Roku - ${newDevice.mac}",
                  "hub": evt?.hubId,
                  "deviceName": null,
                  "host": host,
                  "enabled": false,
                  "apps": [:],
                  "deviceHandlers": [:],
                  "host": host]
    devices[newDevice.mac] = newDevice
    curDevice = newDevice
  } else if (curDevice.host != host) {
    // Host changed, so update device handlers.
    log.debug "Updating ${curDevice.mac} with a new host (${curDevice.host} -> ${host}"
    curDevice.host = host
    // Ensure that all the device handlers use this host.
    curDevice.deviceHandlers.keys().each() { nid ->
      getChildDevice(nid).sync(host)
    }
  }
  // Update the list of apps for this device.
  rokuQueryApps(curDevice)
}

/***
  rokuQueryAppsReponse extracts the list of apps from from the response and
  updates the app list of the correspond rokue device (determined by the mac).
***/
void rokuQueryAppsReponse(physicalgraph.device.HubResponse hubResponse) {
  def mac = "${hubResponse.mac}"
  log.debug "rokuQueryAppsReponse for ${mac}"
  def d = getRokuDevices()[mac]
  if (!d) {
    log.warn "unknowm mac address ($mac) in roku query response: $hubResponse"
    return
  }
  // Update the set of apps for this roku device.
  d.apps = [:]
  hubResponse.xml.children().each {
    d.apps[it.attributes().id] = it.text()
  }
}

/*****************************Utility Functions*******************************/


/*** Internal storage of the detected roku devices ***/
private getRokuDevices() {
  if (!state.rokuDevices) {
    state.rokuDevices = [:]
  }
  state.rokuDevices
}

/*** Used to look up roku device preference ***/
private rokuKey(d) {
  return "${d.mac}"
}

/*** Used to look up roku device handler preference ***/
private dhNameKey(d, name) {
  return "${d.mac}:$name"
}

/*** remoteKeys is a list of valid button codes on the roku remote
  https://sdkdocs.roku.com/display/sdkdoc/External+Control+API#ExternalControlAPI-KeypressKeyValues
***/
private remoteKeys() {
  return ["Power On", "Power Off", "Home", "Rev", "Fwd",
          "Play", "Select", "Left", "Right", "Down", "Up", "Back",
          "InstantReplay", "Info", "Backspace", "Search", "Enter",
          "FindRemote", "Volume Down", "Volume Mute", "Volume Up",
          "Channel Up", "Channel Down", "Input Tuner",  "Input AV1",
          "Input HDMI1", "Input HDMI2", "Input HDMI3", "Input HDMI4"]
}

/******************************Network Functions*******************************/

void ssdpDiscover() {
  log.debug "ssdpDiscover"
  sendHubCommand(new physicalgraph.device.HubAction(
    "lan discovery roku:ecp", physicalgraph.device.Protocol.LAN))
}

void ssdpSubscribe() {
  log.debug "ssdpSubscribe"
  subscribe(location, "ssdpTerm.roku:ecp", ssdpHandler)
  state.subscribe = true
}

private rokuQueryApps(d) {
  sendHubCommand(new physicalgraph.device.HubAction(
    """GET /query/apps HTTP/1.1\r\nHOST: ${d.host}\r\n\r\n""", 
    physicalgraph.device.Protocol.LAN, d.host, [callback: rokuQueryAppsReponse]))
}

private makeHostAddress(ipHex, portHex) {
  return convertHexToIP(ipHex) + ":" + convertHexToInt(portHex)
}

private Integer convertHexToInt(hex) {
  return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
  return [convertHexToInt(hex[0..1]), convertHexToInt(hex[2..3]),
          convertHexToInt(hex[4..5]), convertHexToInt(hex[6..7])].join(".")
}

private hex(value, width=2) {
  def s = new BigInteger(Math.round(value).toString()).toString(16)
  while (s.size() < width) {
    s = "0" + s
  }
  return s
}