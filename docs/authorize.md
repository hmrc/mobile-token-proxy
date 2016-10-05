authorize
----
  Request for the URL to be generated to request web authorization through the API Gateway.
  
* **URL**

  `/oauth/authorize`

* **Method:**
  
  `GET`


The service will return a 303 HTTP response and the Location will be to the API Gateway authorize service.


* **Success Response:**

  * **Code:** 303 <br />

  * **Code:** 500 INTERNAL_SERVER_ERROR <br/>

