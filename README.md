
# mobile-token-proxy

[![Build Status](https://travis-ci.org/hmrc/mobile-token-proxy.svg?branch=master)](https://travis-ci.org/hmrc/mobile-token-proxy) [ ![Download](https://api.bintray.com/packages/hmrc/releases/mobile-token-proxy/images/download.svg) ](https://bintray.com/hmrc/releases/mobile-token-proxy/_latestVersion)

The service acts as a proxy to the API Gateway /oauth/token service and is also responsible for generating the API Gateway authentication HTTP request.


## Endpoints

| Path                     | Supported Methods | Description |
| -------------------------| ------------------| ------------|
|```/oauth/authorize```    | GET               | Request to build an authentication request to the API Gateway. [More...](docs/authorize.md) |
|```/oauth/token```        | POST              | Request for an access-token to make API Gateway service calls. [More...](docs/token.md) |
|```/oauth/taxcalctoken``` | GET               | Request for tax-calc token to make API Gateway service calls. [More...](docs/taxcalctoken.md) |


## Using the service locally.

To test the token service locally then below the services must be running.

```
API_GATEWAY_PROXY, AUTH, AUTH_LOGIN_STUB, USER_DETAILS, MOBILE_TOKEN_PROXY.
```

Once the services are running then follow steps below in order to test the /authorize and /oauth/token services.

1) Open a new browser and enter the below URL into the address bar.

```
/oauth/authorize
```

2) Once the service redirects to the below URL, override the port 9025 with 9949.
Please note since CoAFE is not running, the request will fail. This makes no difference and please override the port and then press return.

```
http://localhost:9025/gg/sign-in?continue=http%3A%2F%2Flocalhost%3A8236%2Foauth%2Fgrantscope%3Fredirect_uri%3Durn%253Aietf%253Awg%253Aoauth%253A2.0%253Aoob%253Aauto%26auth_id%3D26201d52-cf29-4f74-98c5-53126a6a8b60%26scope%3Dread%253Apersonal-income%2Bread%253Acustomer-profile%2Bread%253Amessages%2Bread%253Asubmission-tracker%2Bread%253Aweb-session
```

3) The auth-login-stub will now be displayed. Input the fields PID, NINO and set CL to 200.

4) Once the grant-page is returned obtain the access-code from the title within the page.

5) Make a call to the mobile-token-proxy /oauth/token service supplying the authorizationCode as the access-code.

```
http://localhost:8239/oauth/token
```

The form POST to the above URL will look like below. Please note the authorizationCode is extracted from the access-code in step 4 above.

```
{"authorizationCode":"a4059838-c9d4-4a2f-9539-2c8b4fa60395"}
```

6) The service will return a response like below.

```
{
    "accessToken": "a4059838-c9d4-4a2f-9539-2c8b4fa60395",
    "refreshToken": "a4059838-c9d4-4a2f-9539-2c8b4fa60395",
    "expires_in": 14400
}
```

7) Subsequent calls should supply the refreshToken.

```
{"refreshToken":"a4059838-c9d4-4a2f-9539-2c8b4fa60395"}
```


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").   
