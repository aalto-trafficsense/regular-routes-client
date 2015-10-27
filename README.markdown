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

# 3. Building the client

## 3.1 Define the client configuration

Create a client configuration file in your copy on path: [regularroutes/src/main/assets/regularroutes.conf](https://github.com/aalto-trafficsense/regular-routes-client/blob/master/regularroutes/src/main/assets/regularroutes.conf)

The content is:

    server = "http://address.of.your.server/api"
    queue_size = 4096
    flush_threshold = 24
    web_cl_id = "880100100833-7ama0nPuppuaTaRk0ituksella.apps.googleusercontent.com"

* Set server address: Insert here the http-address of the server (/api) the client is being built for.
* Set the web_cl_id key. Generation of the credentials is done during server installation and explained in the [reguler-routes-devops Readme.markdown](https://github.com/aalto-trafficsense/regular-routes-devops/blob/master/README.markdown). It can be read from [Google developer console](https://console.developers.google.com/) of your project under "APIs & Auth" / "Credentials" / "OAuth 2.0 client IDs" / "Web client 1". "client id for web application" / "client". The sample one above is garbage.

## 3.2 Configure your keystore (if needed)

Please check [further instructions on signing apps from Google](https://developer.android.com/tools/publishing/app-signing.html). Both debug- and release keys are ok. If using the debug-key, it is normally located in `~/.android/debug.keystore`. A keystore for a release key is generated with:

    $ keytool -genkey -v -keystore my-release-key.keystore -alias alias_name -keyalg RSA -keysize 2048 -validity 10000

Remember to record both the keystore password and key password! 
    
## 3.3 Paste your SHA1 fingerprint on the [Google developer console](https://console.developers.google.com/) 

SHA1 from the debug.keystore is extracted like this:

    $ keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android

The SHA1 of a release keystore is extracted with:

    $ keytool -list -v -keystore my-release-key.keystore -alias alias-name

On the console under "APIs & Auth" / "Credentials" / "Credentials": "Add credentials" create an "OAuth 2.0 client ID" with the following information:
    * Application type: Android
    * Signing-certificate fingerprint: Paste the SHA1 as extracted above
    * Package name: From the "AndroidManifest.xml" file in the client: "fi.aalto.trafficsense.regularroutes"
    * Google+ deep linking is not used.
    * Press "Create"

## 3.4 Build

With a debug key: Connect a phone via USB and run from the IDE. The configuration should be ready, but if not, it is "regularroutes" as an "Android Application". Module is "regularroutes", package "Deploy default APK".

With a release key: Select "Build" / "Generate signed APK". Select the proper keystore. Add the proper usernames and passwords. The key alias needs to be updated. For TrafficSense project sample environment the files (separate for test and production servers) are on the project drive. After the .apk-file is generated, copy to phone, install and run.

## 3.5 Build problems & solutions

### 3.5.1 IDE complains about non-Gradle & Gradle modules in the same project

Problem: Opening the client with Intellij IDEA after a new pull from repo, the following error is printed:

    Unsupported Modules Detected
    Compilation is not supported for following modules: regularroutes. Unfortunately you can't have non-Gradle Java modules and Android-Gradle modules in one project.

Solution: Make an arbitrary modification to "settings.gradle" (e.g. add an empty line) and respond "sync now" to the message that appears. The problem should disappear.

### 3.5.2 Errors on missing files

Problem: Gradle refuses to sync, error message:

    Gradle 'regular-routes-client' project refresh failed
    Error:/Users/rinnem2/Dev/AaltoDSG/TrafficSense/regular-routes-client/external/Funf/AndroidManifest.xml (No such file or directory)

Solution: Execute an "update" pull request on all submodules:

    git submodule update --init --recursive

