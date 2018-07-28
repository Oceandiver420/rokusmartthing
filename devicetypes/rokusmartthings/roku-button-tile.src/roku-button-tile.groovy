/**
 *  Copyright 2015 SmartThings
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
 *  Momentary Button Tile that sends commands to a roku device.
 *
 *  Author: Steven Feldman
 *
 *  Date: 2018-07-27
 */
metadata {
  definition (name: "Roku Button Tile", namespace: "RokuSmartThings", author: "SmartThings") {
    capability "Actuator"
    capability "Switch"
    capability "Momentary"
    capability "Sensor"
  }

  // simulator metadata
  simulator {}

  // UI tile definitions
  tiles(scale: 2){
    multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4, canChangeIcon: true){
      tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
        attributeState("off", label: 'Push', action: "momentary.push", backgroundColor: "#ffffff", nextState: "on")
        attributeState("on", label: 'Push', action: "momentary.push", backgroundColor: "#00a0dc")
      } 
    }
    main "switch"
    details "switch"
  }
}

/***
  Sync is called by the Roku Manager SmartApp to update the host for this mac address.
***/
def sync(host) {
  def existingHost = getDataValue("host")
  if (host && host != existingHost) {
    updateDataValue("host", host)
  }
}


def parse(String description) {}

def push() {
  sendEvent(name: "momentary", value: "pushed", isStateChange: true)
  sendRokuCommand()
}

def on() {
  sendEvent(name: "switch", value: "on", isStateChange: true, displayed: false)
  sendRokuCommand()
}

def off() {
  sendEvent(name: "switch", value: "off", isStateChange: true, displayed: false)
  sendRokuCommand()
}

/***
  See ../roku.src/roku.groovy for details.
***/
def sendRokuCommand() {
  def type = getDataValue("type")
  def action = getDataValue("action")
  def contentId = getDataValue("contentId")
  def host = getDataValue("host")
  def urlText

  if (type == "WakeViaLan") {
    log.debug "WakeViaLan on $host for $action"
    sendHubCommand(new physicalgraph.device.HubAction(
      "wake on lan $action", physicalgraph.device.Protocol.LAN, null, [:]))
    return
  }

  if (type == "key") {
    urlText = "/keypress/" + action
  } else if (type == "app") {
    urlText = "/launch/${action}"
    if (contentId) {
      urlText += "?contentId=${contentId}"
    }
  } else{
    log.error "Unknown type: $type (host: $host, action: $action, contentId: $contentId)"
    return
  }

  def httpRequest = [
    method: "POST",
    path: urlText,
    headers: [
      HOST: host,
      Accept: "*/*",
    ]
  ]
  log.debug httpRequest
  sendHubCommand(new physicalgraph.device.HubAction(httpRequest))
}
