/*
 * Copyright (c) 2015, Sergey Parshin, s.parshin@outlook.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of developer (Sergey Parshin) nor the
 *       names of other project contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.github.quarck.calnotify

object Consts
{
	var NOTIFICATION_ID_ERROR = 0;
	var NOTIFICATION_ID_DYNAMIC_FROM = 1;

	//
	val NOTIFICATION_TAG = "com.github.quarck.calnotify.ntftag";

	//
	var INTENT_NOTIFICATION_ID_KEY = "notificationId";
	var INTENT_EVENT_ID_KEY = "eventId";
	val INTENT_TYPE = "intentType"

	val INTENT_TYPE_DELETE = "delete"
	val INTENT_TYPE_DISMISS = "dismiss"
	val INTENT_TYPE_SNOOZE = "snooze"

	//
	var VIBRATION_DURATION : Long = 1000
	var LED_DURATION_ON = 300
	var LED_DURATION_OFF = 2000
	var LED_COLOR = 0x7f0000ff

	val ALARM_THRESHOULD = 3*1000L;
	val SNOOZE_DELAY: Long = 15*60*1000;
}
