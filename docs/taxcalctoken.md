taxcalctoken
----
  Request for tax-calc server token in order to make API Gateway service calls to the tax-calc service.
  
* **URL**

  `/oauth/taxcalctoken`

* **Method:**
  
  `GET`

A successful response to this service returns a server-token.


* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

```json
{
    "token": "some-server-token"
}

* **Error Response:**

  * **Code:** 503 SERVICEUNAVAILABLE<br />
    Failed to communicate to the API Gateway - The call should be re-tried.

  * **Code:** 500 INTERNAL_SERVER_ERROR <br/>



