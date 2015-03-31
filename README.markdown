"Learning Regular Routes" Android client
========================================

# 1. Setting up a development machine


1. Install [Android Studio](https://developer.android.com/sdk/installing/studio.html)<br>
   *or*<br>
   install [IntelliJ IDEA](http://www.jetbrains.com/idea/download/) and [Android SDK](https://developer.android.com/sdk/index.html)
2. Use Android SDK manager (installed by Android Studio or Android SDK) to install at least the following packages:

    + Tools
      + Android SDK Tools
      + Android SDK Platform-tools
      + Android SDK Build-tools 21.1.1
    + Android 5.0 (API 21)
      + SDK Platform
    + Android 4.0.3 (API 15)
      + SDK Platform
    + Extras
      + Android Support Repository
      + Android Support Library
      + Google Play services
      + Google Repository
      
      
  # 2. Documentation
    
  
  ## 2.1 Authentication
  The server-client authentication is done using Google OAuth2 service provided through Google+ API. User doesn't have to have Google+ account; normal Google acocunt is enough.
  
  The registration flow follows Google OAuth2 guidelines. First Android client requests single use token from Google+ API that provides access to users Google profile. This triggers Android to prompt consent from the user before providing client with working google id + token. After receiving token, Android client sends it to the server that verifies its validity from Google API. Both sides calculates 'client id' hash value from users Google id that guarantees same result for any future registrations. That id is used every time when client authenticates later sessions to get a valid session token. That session token is then included in every transaction as a token of valid transaction from client. 
  
When server gets response from Google authentication service, it registers client (stores it's information + received tokens from Google) or responds with "Forbidden" status in case the access token was invalid. Successful registration is responded with HTTP code 200 (OK) + valid session token. Separate authentication API call is not then needed after successful registration.

When client gets response of successful registration, it stores the session token and the client id it calculated previously. With these values stored, client doesn't need to re-register after each time. 

The whole process is described with the following picture that derives the basic elements from Google's OAuth2 description: 
    ![Registration](http://i.imgur.com/A5BpdXA.png)
  
[Source: Google OAuth2 documentation](https://developers.google.com/accounts/docs/OAuth2)

The label circulated with red color shows the order of steps. Google recommends that the authorization token is sent by using secure HTTPS connection with POST request. That implementation requires certification for server. If authorization token is leaked (e.g. via "man in the middle" attack), it still requires server's client id issued by Google to be valid for use. That is not considered to justify the need for proper HTTPS encryption. 

## 2.2 Concept of users and devices
User is identified by Google account. Same user may have several devices that all are linked to the same user. When user registers with different device, same hash value (user id) is used and therefore server will only add a new device for the same user. Otherwise registration does not differ from new user + new device -combination.

Note: When user uninstalls / reinstalls the client application that is considered as a new device (identified by new installation id). 

The data is currently by the device and it is currently not possible to view data of all user's devices at the same time. This may change in the future and therefore every new device sends also device model (name) to the server when they registers. Device name can then be used to provide clear name for each of users devices that have been registered. 
