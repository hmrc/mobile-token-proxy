
# mobile-token-proxy

[![Build Status](https://travis-ci.org/hmrc/mobile-token-proxy.svg?branch=master)](https://travis-ci.org/hmrc/mobile-token-proxy) [ ![Download](https://api.bintray.com/packages/hmrc/releases/mobile-token-proxy/images/download.svg) ](https://bintray.com/hmrc/releases/mobile-token-proxy/_latestVersion)


The micro-service is responsible for returning an access-token which is then used in API Gateway service calls. 

The services main responsibility is
removing the need for applications to communicate to the API Gateway /oauth/token service and remove the need for clients to store the API gateway keys and refresh token on the client.

## Endpoints

| Path              | Supported Methods | Description  |
| ------------------| ------------------| ------------|
|```/oauth/token``` | POST              | Request for an access-token to make API Gateway service calls. [More...](docs/token.md) |

## Using the service locally.

To test this service locally then below the services must be running. 

```
API_GATEWAY_PROXY, AUTH, AUTH_LOGIN_STUB, USER_DETAILS, MOBILE_TOKEN_PROXY and MOBILE_TOKEN_EXCHANGE 
```

Once the services are running then follow steps below.

1) Open a new browser and enter the below URL into the address bar.

```
http://localhost:8236/oauth/authorize?client_id=EzSgJJazdxeTlEsc5GKl6D7qpsUa&redirect_uri=urn:ietf:wg:oauth:2.0:oob:auto&scope=read:personal-income+read:customer-profile+read:messages+read:submission-tracker+read:web-session&response_type=code
```

2) Once the service redirects to the below URL, override the port 9025 with 9949.
Please note since CoAFE is not running, the request will fail. This make no difference and please override the port and then press return. 

```
http://localhost:9025/gg/sign-in?continue=http%3A%2F%2Flocalhost%3A8236%2Foauth%2Fgrantscope%3Fredirect_uri%3Durn%253Aietf%253Awg%253Aoauth%253A2.0%253Aoob%253Aauto%26auth_id%3D26201d52-cf29-4f74-98c5-53126a6a8b60%26scope%3Dread%253Apersonal-income%2Bread%253Acustomer-profile%2Bread%253Amessages%2Bread%253Asubmission-tracker%2Bread%253Aweb-session
```

3) The auth-login-stub will now be displayed. Input the fields PID, NINO and set CL to 200.

4) Once the grant-page is returned obtain the access-code from the title within the page.

5) Make a call to the mobile-token-proxy /oauth/token service supplying the authorizationCode as the access-code.

6) The service will return a response like below.

```
{
    "accessToken": "50add4ed-4a87-408e-bf8a-bde68b2a2444",
    "ueid": "qBTGM/9UB/KtPQBMa6RgSQ==",
    "expireTime": 14400
}
```

7) Subsequent invocations to the service /oauth/token, the client must supply the ueid attribute that was returned in the previous request. Each new call to /oauth/token supplying a ueid, will always return a new ueid value.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
    