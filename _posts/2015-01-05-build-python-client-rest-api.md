---
layout: post
title: "A Python DSL for building REST clients"
date: 2015-01-05
comments: true
categories: python
---

> In this article, I implemented a mini-dsl in Python for describing REST
> APIs. This mini-dsl allows developers to intuitively build a Python HTTP
> client easily.

When I worked at Sense Tecnic Systems, I needed to write a Python client for our
REST API. We had another guy who wrote the API up, and the code shocked me. We
had a file with 2000 lines, and every function wlooked like this:

```python
def send_data_post(self, sensor_id, data, username = None, password = None):
    """ Send new data to a sensor.

    :param sensor_id: Sensor ID to send data to.
    :type sensor_id: str.
    :param data: Data to send to this sensor.
    :type data: dict
    :param username: If provided with password, overrides the default login credentials supplied on initialization.
    :type username: str.
    :param password: Used in combination with username.
    :type password: str.
    :raises: WotkitException if a status code is not 200's"""
    #use SenseTecnic
    #log.info("Sending data to wotkit for sensor " + sensor + ": " + str(attributes))
    sensor_id = str(sensor_id)
    auth_credentials = self._get_login_credentials(username, password)
    url = self.api_url+'/sensors/'+sensor_id+'/data'
    try:
        response = requests.post(url = url, auth=auth_credentials, data = data)
    except Exception as e:
        raise WotkitException("Error in sending new data by POST to sensor at url: " + url + ". Error: " + str(e))

    if response.ok:
        log.debug("Success sending POST sensor data to url: " + url)
        return True
    else:
        raise WotkitException("Error in sending new data by POST to sensor at url: " + url + "\n Response Code: " + str(response.status_code) + "\n Response Text: " + response.text.encode(response.encoding))
```

## Can we do better?

I abhorred this code. Was this style of coding *really* necessary? REST APIs
have a regular pattern. I myself found it unintuitive to work with this wrapper
everyday because we are bastardizing a REST API into an RPC-style one.

So, instead of this crap, can we somehow express API access REST-like. i.e.

```python
# Equivalent to: GET http://wotkit.sensetecnic.com/api/v2/sensors/234/data
API = Base("http://wotkit.sensetecnic.com/api/v2")
data = API.sensors.id(234).data.GET()
```

<center>
Ofcourse we can!
</center>

---------------------------

## Mapping the URLs
For developers trying to map out their REST API, it should be a simple process
of mapping different URL fragments. Each fragment is represented as a
**RequestHandler** which should:

1. Invoke HTTP Methods like **GET**, **POST**, **PUT**, **DELETE**
2. Link to sub-**RequestHandlers**.

A definition of a **RequestHandler** should:

1. Describe the URL Fragment it represents
1. Define allowed requestMethods
2. Define a mapping from this **RequestHandler** to **sub-RequestHandlers**

This is what our mapped REST API looks like with this DSL.

```python
class Base(RequestHandler):
    """ Base URL Fragment which has knowledge about magical bits around it """
    requestMethods = ["GET", "POST", "DELETE", "PUT"]

    requestMapping = {
        "users": User,
        "orgs": Organization,
        "organization": Organization,
        "sensors": Sensor,
    }

    def __init__(self, api_url):
        if isinstance(api_url, list):
            self.fragments = api_url
        else:
            self.fragments = [api_url]
        self.options = {}


class Organization(RequestHandler):
    fragmentUrl = "orgs"
    requestMapping = {"members": OrgMember}


class User(RequestHandler):
    fragmentUrl = "users"

class Sensor(RequestHandler):
    fragmentUrl = "sensors"
    requestMapping = {
        "domain_id": DomainId,
        "data": SensorData,
        "metrics": Metric
    }
```

This lets you use the API like so:

```python
API = Base("http://wotkit.sensetecnic.com/api/v2")

# This represents the path: `wotkit.sensetecnic.com/api/v2/api/sensors/foo
mySensorPath = API.sensors.id("foo")

# This is equivalent to: `GET wotkit.sensetecnic.com/api/v2/api/sensors/foo`
mySensor = mySensorPath.GET()

# This is equivalent to: `GET wotkit.sensetecnic.com/api/v2/api/sensors/foo/data`
data = mySensor.data.GET()

# Or all at once: `GET wotkit.sensetecnic.com/api/v2/api/sensors/foo/data`
API.sensors.id("foo").data.GET()
```

## Implementation

The **RequestHandler** parent class does 100% of the heavy lifting. Python objects
can override `__getattr__` which lets us generate attributes on the fly.

In my implementation below, `__getattr__` looks up each a RequestHandler's
**requestMethods** and **requestMapping** fields to navigate a REST API. I also
overrided `__dir__` so that IPython can autocomplete auto-genenerated fields.

I needed a bit of boilerplate code to implement **Id** fragments. Each **Id**
fragment needs to the requestMapping from it's parent **RequestHandler**.

In the code below for example, `mySensor` needs to inherit the requestMapping
from `API.sensors`.

```python
API = Base("http://wotkit.sensetecnic.com/api/v2")
mySensor = API.sensors.id("123")
```

### Neat Trick:  Enumerate an entire API
Since the API uses Python datastructures, we can easily enumerate through all
the endpoints. With this, you can use it to smoke test your entire API!

```python
def enumerateAPI(resource):
    """ Enumerate the entire API as a list of fragments we want to evaluate """
    currentResource = resource.id(1)
    subResources = map(lambda k: currentResource.__getattr__(k), currentResource.requestMapping.keys())
    totalResources = [item for sublist in [enumerateAPI(i) for i in subResources] for item in sublist]
    totalResources.append(currentResource)
    totalResources.append(resource)
    return totalResources

API = Base("http://wotkit.sensetecnic.com/api/v2")
test_apis = enumerateAPI(base)


for api in test_apis:
    # Smoke test them in here!
```


## Source
Here's the source code for the entire dsl + endpoints.

<details markdown="1">
<summary markdown="span">Example Code</summary>

```python
import requests
import json

class RequestHandler(object):
    """ Base URL Fragment which has knowledge about magical bits around it """

    requestMethods = ["GET", "POST", "DELETE", "PUT"]
    requestMapping = {}

    def __init__(self, fragments):
        self.fragments = fragments

    def __getattr__(self, name):
        """ Defer all the magic here: if we get a requestMethod, we return a
        function which can be invoked using the requests library, otherwise we
        build a map using the meta shit defined below"""

        if (name == "id"):
            # Prevent base nesting itself with ids
            if (self.__class__ == Base):
                return self

            return IdFragment(self.fragments, self.__class__)

        elif (name in self.requestMethods):
            url = "/".join(self.fragments)
            return RequestWrapper(method=name, url=url)

        elif (name in self.requestMapping):
            # print "attempted request map %s" % (name)
            # TODO: add error handling here if requestMapping does not exist in name
            nextObj = self.requestMapping[name]
            new_fragments = list(self.fragments)
            new_fragments.append(nextObj.fragmentUrl)
            return nextObj(new_fragments)

        else:
            raise AttributeError("API has no subpath: %s. Allowed: %s" %
                                 (name, ",".join(self.requestMapping.keys())))

    def __str__(self):
        return "/".join(self.fragments)

    def __dir__(self):
        return sorted(set(
            dir(type(self)) + self.__dict__.keys() + self.requestMethods
            + self.requestMapping.keys()
        ))

    def __repr__(self):
        return "<" + str(self) + ">"

class RequestWrapper:
    """ Wraps up a Request object so that appending options has the same API """
    def __init__(self, method, url):
        headers = {"content-type": "application/json"}
        self.options = {"method": method, "url": url, "headers": headers}

    def __call__(self, **kwargs):
        """ Delegates work to the requests library below. Passes options to the requests library
        with the notable extras:
         - auth encodes with basic-auth. it will take either a tuple consisting of
           (username, password) or convert any dictionary with username and password fields set
        """

        options = dict(self.options.items() + kwargs.items())

        data = options.get("data")
        if (data is not None):
            content_type = options["headers"]["content-type"]

            if (content_type == "application/json"):
                options["data"] = json.dumps(data)

        r = requests.Request(**options).prepare()
        s = requests.Session()
        return s.send(r)


class IdFragment(RequestHandler):
    """ Returns an id handler which follows the mapping of the preceding API """

    def __init__(self, fragments, handlerClass):
        self.fragments = fragments
        self.handlerClass = handlerClass

    def __call__(self, value):
        """ Append this fragment to itself """
        new_fragments = list(self.fragments)
        new_fragments.append(str(value))
        return self.handlerClass(new_fragments)

###############################################################################
#                       DOMAIN SPECIFIC MAPPINGS
###############################################################################
class Application(RequestHandler):
    fragmentUrl = "apps"


class SensorData(RequestHandler):
    fragmentUrl = "data"


class DomainId(RequestHandler):
    requestMethods = ["GET", "PUT"]
    fragmentUrl = "domain_id"


class Metric(RequestHandler):
    fragmentUrl = "metrics"


class Sensor(RequestHandler):
    fragmentUrl = "sensors"
    requestMapping = {
        "domain_id": DomainId,
        "data": SensorData,
        "metrics": Metric
    }


class OrgMember(RequestHandler):
    fragmentUrl = "members"


class User(RequestHandler):
    fragmentUrl = "users"


class Organization(RequestHandler):
    fragmentUrl = "orgs"
    requestMapping = {"members": OrgMember}


class Base(RequestHandler):
    """ Base URL Fragment which has knowledge about magical bits around it """
    requestMethods = ["GET", "POST", "DELETE", "PUT"]

    requestMapping = {
        "apps": Application,
        "users": User,
        "orgs": Organization,
        "organization": Organization,
        "sensors": Sensor,
    }

    def __init__(self, api_url):
        if isinstance(api_url, list):
            self.fragments = api_url
        else:
            self.fragments = [api_url]
        self.options = {}

    def __call__(self, *args):
        return self

```

</details>
