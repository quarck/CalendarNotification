Variables to consider: 

 * Quiet hours status: 
 	- on
	- off

 * Quiet hours perod: 
 	- 22:00 - 6:00
	- 9:00 - 18:00

 * Current time * quiet hours period
 	- current time / 22:00 / 6:00
	- 22:00 / current time / 00:00 / 6:00
	- 22:00 / 00:00 / current time / 6:00
	- current time / 00:00 / 9:00 / 18:00
	- 00:00 / current time / 9:00 / 18:00
	- 9:00 / current time / 18:00 

 * Next snoozed time / event time - multiple of prev 

 * Next reminder time - miltiple of prev

 * Is primary event muted 

 * Are reminders afer snooze period active 

 * Is it a primary event or snoozed 
 
 * Max remindres - limited / unlimited 



 * Make sure to test additionally: 

   - primary event in quiet hours, muted primary - reminder should fire after quiet, during quiet - silent post
   - primary event in quiet hours, not muted, - proper sound, no reminder 
   - snoozed event firing in quiet hours - reminder should fire after quiet 

   in all above 3 cases - if regular reminders are active, they should start firing periodically after quiet hours
