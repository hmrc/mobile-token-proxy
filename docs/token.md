token
----
  Request for an access-token in order to make API Gateway service calls. This service removes the need for client applications to call the API Gateway service /oauth/token.
  
* **URL**

  `/oauth/token`

* **Method:**
  
  `POST`

*  **JSON**

Please note the deviceId is the cookie mdtpdi.

```json
{
  "deviceId": "some device Id",
  "authorizationCode": "some auth-code",
  "ueid": "some ueid"
}
```

Please note: Either supply the authorizationCode (returned from the API Gateway grant-page) or the ueid. Both attributes must not be supplied.

The first time a user logs into the system, the grant-page will return an authorization-code and this value will be supplied in the authorizationCode attribute.

Once a successful response to this service returns an ueid attribute, then subsequent calls to this service will supply the ueid.



* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 



```json
{
  "accessToken": "some access token",
  "ueid": "some euid",
  "expireTime": 123456789
}```

Please note the ueid is an optional attribute. If the attribute is not returned then this indicates a failure occurred with communicating to the mobile-token-exchange service. If the UEID is not returned then the client will need to login again once the access-token expires.

Should the ueid field be returned, the client must store this value on the client in order to supply to subsequent calls to this service.

The accessToken is used in all API Gateway calls as the authentication header. The expireTime is the amount of time the accessToken is valid before a 401 is returned from the API Gateway. Once the gateway returns a 401 response, the client will call this service supplying the ueid to obtain a new access-token.

Each call to this service supplying a valid ueid attribute, will return a new ueid attribute that must then be passed in subsequent service calls.


* **Error Response:**

  * **Code:** 400 BADREQUEST <br />
    The POST request to the service is invalid.

  * **Code:** 401 UNAUTHORIZED <br />
    The UEID supplied is invalid.

  * **Code:** 404 NOTFOUND <br />
    The deviceId could not be found.

  * **Code:** 503 SERVICEUNAVAILABLE<br />
    Failed to communicate to the API Gateway

  * **Code:** 500 INTERNAL_SERVER_ERROR <br/>



