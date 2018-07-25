/**
 *  Roku Virtual Buttons
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
 *  Code inspired by Sam Steele (https://github.com/c99koder/SmartThings)
 * 
 *  TODO: add key_sequence support.
 *		- UI page to specify sequence
 *		-  test speed?
 */

definition(
	name: "Roku Virtual Buttons",
	namespace: "RokuSmartThings",
	author: "Steven Feldman",
	description: "Enables users to create virtual buttons to control a Roku TV",
	category: "SmartThings Labs",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

preferences {
	page(name: "rokuPage", title:"Roku Selection", nextPage: "buttonPage", uninstall: true) {
		section("Select your Roku") {
			input name: "roku", type: "capability.mediaController", title: "Roku Device", multiple: false, required: true
		}
	}
	page(name: "buttonPage")
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def buttonPage() {
    log.debug "buttonPage"
    dynamicPage(name: "buttonPage", title:"Button Selection", uninstall: false, install: true) {
    	setAvailableButtons()
        section("Select desired buttons") {
        	state.button_pref.values().each() {
				input name: it.id, type: "bool", title: it.label, required: false
            }
		}
    }
}

/***
 setAvailableButtons - generates a list of available roku buttons.
 If there was an enabled button that is no longer availble, keep it in the list.
 ***/
def setAvailableButtons() {
	def button_pref = [:]
	def cur_button_pref = [:]
    if (state.button_pref) {
    	cur_button_pref = state.button_pref
    }

	// https://sdkdocs.roku.com/display/sdkdoc/External+Control+API#ExternalControlAPI-KeypressKeyValues
	def keys = [
    	"Wake Via Lan", "Power On", "Power Off", "Home", "Rev", "Fwd", "Play",
    	"Select", "Left", "Right", "Down", "Up", "Back", "InstantReplay",
        "Info", "Backspace", "Search", "Enter", "FindRemote",
        "Volume Down", "Volume Mute", "Volume Up",
        "Channel Up", "Channel Down", "Input Tuner",  "Input AV1",
        "Input HDMI1", "Input HDMI2", "Input HDMI3", "Input HDMI4"
    ]
    keys.each{
		def id = it.replaceAll(' ', '')
		button_pref[id] = [
			id: id,
			label: it,
            type: "key",
			enabled: cur_button_pref.containsKey(id) && cur_button_pref[id].enabled
		]
	}

	def activityList = roku.currentValue("activityList")
	if (activityList != null) {
		def appsNode = new XmlSlurper().parseText(activityList)
		appsNode.children().each{
            def id = it.@id.toString()
            if (id == null) { return }
			button_pref[id] = [
				id: id,
				label: it.text(),
                type: "app",
			  	enabled: cur_button_pref.containsKey(id) && cur_button_pref[id].enabled
		  ]
		}
	}
	// Add any enabled buttons that are no longer valid. If the user deselects them,
	// they will be removed from the list.
	cur_button_pref.values().each{
		if (it.enabled && !button_pref.containsKey(it.id)) {
			button_pref[it.id] = it
		}
	}
    state.button_pref = button_pref
}

def initialize() {
	// log.debug "List of options: ${state.button_pref}"
    
    // Iterate over the button options and update the enabled list.
	state.button_pref.values().each() {
        it.enabled = settings.get(it.id, false)
		if (getChildDevice(it.id) == null && it.enabled) {
        	def device = addChildDevice("RokuSmartThings", "Roku Button Tile", it.id, null, [label: "Roku: ${it.label}"])
			state["$device.id"] = it.id
			log.debug "Created button tile $device.id for channel ${it.label} (${it.id})"
        }
    }

	// After adding new devices, go through and remove old ones and re-subscribe
	getAllChildDevices().each {
        def id = state[it.id]
        // Ensure that disabled buttons are removed.
    	if (!state.button_pref.get(id) || !state.button_pref.get(id).enabled) {
        	log.debug "deleting $it (id=$id) because it is disabled."
        	deleteChildDevice(id)
            return
        }
		subscribe(it, "switch", switchHandler)
	}
}

def switchHandler(evt) {
	// Only respond to 'on' events
	if (evt.value != "on") {
		log.warn "unexpected event: $evt.value"
		return
	}
    // Get the device id and the pref.
    def b = state.button_pref[state["$evt.device.id"]]
    if (!b) {
    	log.error "Event from button not in pref. $evt $id"
    	return
    }

	if (b.id == "wakeViaLan") {
		rokue.wakeViaLan()
		return
	}
    if (b.type == "app") {
    	roku.launchAppId(b.id)
        return
    }
    if (b.type == "key") {
    	roku.pressKey(b.id)
        return
    }
    if (b.type == "key_sequence") {
    	for (String key : b.keys) {
        	roku.pressKey(key)
        }
        return
    }
    log.error "unknown type: $evt $id $b"
}