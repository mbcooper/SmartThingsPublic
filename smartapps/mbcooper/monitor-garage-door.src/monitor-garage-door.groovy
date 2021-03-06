/**
 *  Monitor Garage Door
 *
 *  Copyright 2015 Michael Cooper
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
 */
definition(
    name: "Monitor Garage Door",
    namespace: "mbcooper",
    author: "Michael Cooper",
    description: "Monitors garage door.  If open more than N minutes, takes action that depends on Mode.  If Away or Night, close.  if Home, notify.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact@2x.png")



preferences {
	section("When the garage door is open...") {
		input "garageSensor", "capability.contactSensor", title: "Which?", required: true
	}
	section("Alert Threshold") {
		input "maxOpenTime", "number", title: "Minutes?", required: true
        }
    section("Repeats"){
	   	input "alertBool", "bool", title: "Repeat?"
    }
	section("Text me at (optional, sends a push notification if not specified)...") {
        input("recipients", "contact", title: "Notify", description: "Send notifications to") {
            input "phone", "phone", title: "Phone number?", required: false
        }
	}
    section("Send close door command"){
    	input "garageOpener", "capability.relaySwitch", title: "Which", required: true
    }
}

def installed()
{
	subscribe(garageSensor, "contact", garageSensorHandler)
    initialize()
}

def updated()
{
	unsubscribe()
    initialize()
	
}
def initialize(){
	
}

def garageSensorHandler(evt) {

	def myGarageDoorSensor = garageSensor.contact
    if(myGarageDoorSensor == "closed"){
    	clearStatus()
        clearSmsHistory()
        log.debug "Garage door is closed"
        return
    }
    
    // is open
   def isNotScheduled = state.status != "scheduled"

    if (isNotScheduled) {
        runIn(maxOpenTime * 60, takeAction, [overwrite: false])
        state.status = "scheduled"
        log.debug "Scheduled for action"
    }

	
}

def takeAction(){
	if (state.status == "scheduled")
	{
		def deltaMillis = 1000 * 60 * maxOpenTime
        log.debug "delta in ms ${deltaMillis}"
		def timeAgo = new Date(now() - deltaMillis)
        def dateCreated = garageSensor.contactState.dateCreated
        log.debug " sensor created ${dateCreated}"
		def openTooLong = dateCreated.toSystemDate() < timeAgo
		log.debug "open too long? ${openTooLong}"
		
       // def recentTexts = state.smsHistory.find { it.sentDate.toSystemDate() > timeAgo }

		//if (!recentTexts) {
		//	sendTextMessage()
		//}
		//runIn(maxOpenTime * 60, takeAction, [overwrite: false])
	} else {
		log.trace "Status is no longer scheduled. Not sending text."
	}
}

def sendTextMessage() {
	log.debug "Door was open too long, texting $phone"

	updateSmsHistory()
	def openMinutes = maxOpenTime * (state.smsHistory?.size() ?: 1)
	def msg = "Your Garage Door has been open for more than ${openMinutes} minutes!"
    if (location.contactBookEnabled) {
        sendNotificationToContacts(msg, recipients)
    }
    else {
        if (phone) {
            sendSms(phone, msg)
        } else {
            sendPush msg
        }
    }
}

def updateSmsHistory() {
	if (!state.smsHistory) state.smsHistory = []

	if(state.smsHistory.size() > 9) {
		log.debug "SmsHistory is too big, reducing size"
		state.smsHistory = state.smsHistory[-9..-1]
	}
	state.smsHistory << [sentDate: new Date().toSystemFormat()]
}

def clearSmsHistory() {
	state.smsHistory = null
}

def clearStatus() {
	state.status = null
}
