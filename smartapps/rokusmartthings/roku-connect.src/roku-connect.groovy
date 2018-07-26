/**
 *  Roku Device Manager
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
 * This code was heavily based on github.com/MadMouse/SmartThings
 *
 * Notes/Issues/TODOSs:
 * - For Setting up ruko's, replace t/f enable with a string name.
 * - roku page may not refresh as fast as it should
 * - preference values are not persisted?
 */

definition(
  name: "Roku Device Manager",
  namespace: "RokuSmartThings",
  author: "Steven Feldman",
  description: "Manage Roku Devices",
  singleInstance: true,
  category: "SmartThings Labs",
  iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
  iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
  iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

def urnPlayerLink = "urn:roku-com:device:player:1"

preferences {
  page(name: "deviceDiscovery", nextPage: "createButtons")
  page(name: "createButtons")
}

def deviceEnabledKey(device) {
  "device_${device.mac}"
}

def dhEnabledKey(device, name) {
  "dh_${device.mac}_$name"
}

def deviceDiscovery() {
  log.debug "deviceDiscovery"
  int refreshCount = !state.refreshCount ? 0 : state.refreshCount as int
  state.refreshCount = refreshCount + 1
  def refreshInterval = 5

  if (!state.subscribe) {
    log.debug "subscribe :: ${refreshCount}"
    ssdpSubscribe();
  }
  
  // ssdp request every 25 seconds
  if ((refreshCount % 5) == 0) {
    ssdpDiscover()
  }
  
  // setup.xml request every 5 seconds except on discoveries
  // if (((refreshCount % 1) == 0) &&
  //     ((refreshCount % 5) != 0)) {
  //   log.debug "verifyDevices :: ${refreshCount}"
  //   verifyDevices()
  // }

  return dynamicPage(name: "deviceDiscovery", title: "Discovery Started!",
                     nextPage: "createButtons", refreshInterval: 5, install: false, uninstall: true) {
    section("Please wait while we discover your Roku Devices. Discovery can " +
            "take five minutes or more, so sit back and relax! Select your " +
            "device below once discovered.") {
      getVerifiedDevices().values().each {
        input name: deviceEnabledKey(it), type: "bool", title: it.name, required: true
        // TODO: change to string and update help text.
      }
    }
  }
}

def createButtons() {
  log.debug "createButtons"
  updateDeviceSelection()

  return dynamicPage(name: "createButtons", title:"Create Optional Buttons",
                     uninstall: false, install: true) {
    def found = false
    getDevices().values().each { device ->
      if (!device.enabled) { return }
      found = true
      section("Select desired buttons for '${device.name}'") {
        device.buttons.each() { k, v ->
          input name: dhEnabledKey(device, k), type: "bool", title: v.label, required: true
          // TODO: default values???
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
  state.devices = [:]
}

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

def getDevices() {
  if (!state.devices) {
    state.devices = [:]
  }
  state.devices
}

/***
updateDeviceSelection iterates through the devices sets whether or not they enabled according to
the current preferences.
***/
def updateDeviceSelection() {
  getDevices().values().each { device ->
    device.enabled = settings[deviceEnabledKey(device)]
    if (device.enabled) {
      setAvailableButtons(device)
    }
  }
}

def removeChildDevices(device) {
  log.debug "deleteDeviceChildren"
  def childIds = [:]
  childIds[device.mac] = true
  device.handlers?.keys().each { name ->
    childIds[dhEnabledKey(device, name)] = true
  }
  def children = getChildDevices()?.find {
    childIds[it.deviceNetworkId]
  }
  if (children) {
    children.each {
      deleteChildDevice(it.deviceNetworkId)
    }
  }
}

def updateDeviceHandlers() {
  log.debug "updateDeviceHandlers"
  
  getDevices().values().each { device ->
    if (!device.enabled) {
      removeChildDevices(device)
      return
    }

    // Create device handlers for this roku device.
    def roku = getChildDevices()?.find { it.deviceNetworkId == rokuDNI }
    if (!roku) {
      log.debug "Creating Roku Device with dni: ${device.mac}" + 
                " - ${device.networkAddress} : ${device.deviceAddress}"
      def data = [
        "label": device.name,
        "data": ["mac": device.mac, "ip": device.networkAddress, "port": device.deviceAddress]
      ]
      roku = addChildDevice("RokuSmartThings", "Roku", device.mac, device.hub, data)
    }

    device.buttons.each { name, button ->
      def hid = dhEnabledKey(device, name)
      def cd = getChildDevices()?.find { it.deviceNetworkId == hid }

      if (!button.enabled && cd) {
        log.debug "removing device handler: $hid"
        deleteChildDevice(hid)
        return
      } else if (button.enabled && !cd) {
        log.debug "Creating ${device.name}'s ${button.label}"
        // Create handler.
        cd = addChildDevice("RokuSmartThings", "Roku Button Tile", hid, null,
          ["label": "${roku} ${button.label}", // TODO: change prefix to match roku
            "data": ["rokuID": device.mac, "type": button.type, "value": button.value]])
      }
      subscribe(cd, "switch", rokuButtonHandler)
    }    
  }
}


def rokuButtonHandler(evt) {
  // Only respond to 'on' events
  log.debug "rokuButtonHandler"
  if (evt.value != "on") {
    return
  }

  def rokuID = evt.device.currentValue("RokuDeviceID")
  def type = evt.device.currentValue("CommandType")
  def value = evt.device.currentValue("CommandValue")
  def roku = getChildDevices()?.find { it.deviceNetworkId == rokuID }
  if (!roku) {
    log.error "could not find roku: $rokuID, $type, $value"
    return
  }

  if (value == "WakeViaLan") {
    roku.wakeViaLan()
    return
  }
  if (type == "app") {
    roku.launchAppId(value)
    return
  }
  if (type == "key") {
    roku.pressKey(value)
    return
  }
  log.error "unknown type: $evt"
}

// https://sdkdocs.roku.com/display/sdkdoc/External+Control+API#ExternalControlAPI-KeypressKeyValues
def remoteKeys() {
  return ["Wake Via Lan", "Power On", "Power Off", "Home", "Rev", "Fwd",
          "Play", "Select", "Left", "Right", "Down", "Up", "Back",
          "InstantReplay", "Info", "Backspace", "Search", "Enter",
          "FindRemote", "Volume Down", "Volume Mute", "Volume Up",
          "Channel Up", "Channel Down", "Input Tuner",  "Input AV1",
          "Input HDMI1", "Input HDMI2", "Input HDMI3", "Input HDMI4"]
}

/***
 setAvailableButtons - generates a list of available roku buttons.
 If there was an enabled button that is no longer availble, keep it in the list.
 ***/
def setAvailableButtons(device) {
  def buttons = [:]
  
  // Note: enabled is modified via preferences.
  remoteKeys().each() {
    def id = it.replaceAll(' ', '')
    buttons[id] = [
      label: it,
      type: "key",
      enabled: false,
      value: id
    ]
  }

  def roku = getChildDevices()?.find { it.deviceNetworkId == device.mac }
  def activityList = roku.getActivityList()
  if (activityList != null) {
    def appsNode = new XmlSlurper().parseText(activityList)
    appsNode.children().each() {
      def id = it.@id.toString()
      if (id == null) { return }
      buttons[id] = [
        label: it.text(),
        type: "app",
        enabled: false,
        value: id
      ]
    }
  }

  // Copy over buttons that are not enabled but not in the current activity list.
  def cur_buttons = device.buttons
  cur_button?.values().each() {
    if (!it.enabled) { return }
    it.enabled = false
    buttons[it.id] = it 
  }

  // Set the enabled state
  buttons.each() { k, v ->
    v.enabled = settings[dhEnabledKey(device, k)]
  }

  // update the button list.
  device.buttons = buttons
}

def parse(description) {
  log.trace "parse : " + description
}

def ssdpHandler(evt) {
  def description = evt.description
  def hub = evt?.hubId

  def parsedEvent = parseLanMessage(description)
  parsedEvent << ["name": parsedEvent.name ?: "Roku - ${parsedEvent.mac}"]
  parsedEvent << ["hub": hub]
  parsedEvent << ["enabled": false]
  parsedEvent << ["buttons": [:]]

  def devices = getDevices()
  String ssdpUSN = parsedEvent.ssdpUSN.toString()
  if (devices."${ssdpUSN}") {
    log.debug "found existing device ssdpUSN ${ssdpUSN}"
    def d = devices."${ssdpUSN}"
    if (d.networkAddress != parsedEvent.networkAddress || d.deviceAddress != parsedEvent.deviceAddress) {
      d.networkAddress = parsedEvent.networkAddress
      d.deviceAddress = parsedEvent.deviceAddress
      def child = getChildDevice(parsedEvent.mac)
      if (child) {
        child.sync(parsedEvent.networkAddress, parsedEvent.deviceAddress)
      }
    }
  } else {
    log.debug "creating new device ssdpUSN ${ssdpUSN}: ${parsedEvent}"
    devices << ["${ssdpUSN}": parsedEvent]
  }
}

def getVerifiedDevices() {
  log.debug "getVerifiedDevices"
  getDevices()
}

// Map verifiedDevices() {
//   def devices = getVerifiedDevices()
//   def map = [:]
//   devices.each {
//     def value = it.value.name ?: "Roku ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
//     def key = it.value.mac
//     map["${key}"] = value
//   }
//   map
// }
// 
// 
// void verifyDevices() {
//   def devices = getDevices()
//   devices.each {
//     int port = convertHexToInt(it.value.deviceAddress)
//     String ip = convertHexToIP(it.value.networkAddress)
//     String host = "${ip}:${port}"
//     log.debug "Veryifing ${host}"
//     sendHubCommand(new physicalgraph.device.HubAction(
//       """GET ${it.value.ssdpPath} HTTP/1.1\r\nHOST: ${host}\r\n\r\n""",
//       physicalgraph.device.Protocol.LAN, host, [callback: deviceDescriptionHandler]))
//   }
// }
//
// void deviceDescriptionHandler(physicalgraph.device.HubResponse hubResponse) {
//   log.debug "deviceDescriptionHandler"
//   def body = hubResponse.xml
//   def devices = getDevices()
//   def device = devices.find { it?.key?.contains(body?.device-info?.udn?.text()) }
//   if (device) {
//     log.debug "device verified"
//     device.value << [
//       name: body?.device-info?.user-device-name?.text(),
//       model: body?.device-info?.model-number?.text(),
//       serialNumber: body?.device-info?.serial-number?.text(),
//       verified: true
//     ]
//   } else {
//     log.warn "${body?.device-info} not in devices"
//   }
// }
// 
// private Integer convertHexToInt(hex) {
//   Integer.parseInt(hex,16)
// }
// 
// private String convertHexToIP(hex) {
//   [convertHexToInt(hex[0..1]),
//    convertHexToInt(hex[2..3]),
//    convertHexToInt(hex[4..5]),
//    convertHexToInt(hex[6..7])].join(".")
// }

// private Boolean canInstallLabs() {
//   return hasAllHubsOver("000.011.00603")
// }

// private Boolean hasAllHubsOver(String desiredFirmware) {
//   return realHubFirmwareVersions.every { fw -> fw >= desiredFirmware }
// }

// private List getRealHubFirmwareVersions() {
//   return location.hubs*.firmwareVersionString.findAll { it }
// }
