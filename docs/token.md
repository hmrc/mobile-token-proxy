token
----
  Request for tokens in order to make API Gateway service calls. This service removes the need for client applications to call the API Gateway service /oauth/token.
  
* **URL**

  `/oauth/token`

* **Method:**
  
  `POST`

*  **JSON**

```json
    {"authorizationCode":"a4059838-c9d4-4a2f-9539-2c8b4fa60395"}
```

OR

```json
    {"refreshToken":"a4059838-c9d4-4a2f-9539-2c8b4fa60395"}
```


Please note: Either supply the authorizationCode (returned from the API Gateway grant-page) or the refresh-token. Both attributes must not be supplied.

The first time a user logs into the system, the grant-page will return an authorization-code and this value will be supplied in the authorizationCode attribute.

Once a successful response to this service returns a refresh-token attribute, then subsequent calls to this service must supply the refresh-token.

The response body can contain a JSON response from the API Gateway which contains both 'code' and 'message' attributes.

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

```json
{
    "accessToken": "a4059838-c9d4-4a2f-9539-2c8b4fa60395",
    "refreshToken": "a4059838-c9d4-4a2f-9539-2c8b4fa60395",
    "expires_in": 14400
}
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    The token supplied is invalid and a new token is required.

  * **Code:** 503 SERVICEUNAVAILABLE<br />
    Failed to communicate to the API Gateway - The call should be re-tried.

  * **Code:** 500 INTERNAL_SERVER_ERROR <br/>



