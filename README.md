# Calendar Notifications Plus
Android app extending calendar notifications with snooze button and notifications persistance

<a href="https://f-droid.org/repository/browse/?fdid=com.github.quarck.calnotify" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"/></a>
<a href="https://play.google.com/store/apps/details?id=com.github.quarck.calnotify" target="_blank">
<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="80"/></a>

**If this app would ever get less than 4.0 rating on average on play store, it would be pulled from play store and would be only distributed ia F-Droid and (possibly) Amazon App Store. :-) **

You can also build the app yourself from sources available here :)

This app would replace default calendar event notifications, providing snooze functionality and notifications persistence. Reboot is handled, all notifications are restored after reboot. Focus of this app is to keep its operation as transparent as possible, calendar notifications would behave like expected: direct click on notification opens event details in default calendar application, new functionality is provided via actions available for notifications.
On the snooze activity you can also quickly re-schedule event for the next day or week in one click (this is not available for repeating events).

Additional functionality provided by this app: 
* Quiet hours
* Reminders for missed notifications (off by default, interval can be configured)
* "Snooze All" button in the app
* Custom LED colors / Wake screen on notification (if configured)

This app is currently in BETA. Please report any bugs found and any feedback you have via feedback page in the app.

Rationale for requested permissions: 
* Read Calendar - required to be able to retrieve event details to display notification
* Write Calendar - necessary to stop stock calendar from showing the same notification 
* Start at Boot - to restore notifications
