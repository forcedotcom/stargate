## Stargate

> "It was a small outpost: a nexus, assimilator, and a couple of gateways." -- Flight Sergeant Aaron Keebler
> The stargate is an advanced protoss structure.

Stargate is a Play Scala Module that adds API endpoints to your app to enable integration with Salesforce Marketing Cloud Single Sign On.

## How to Add Stargate to Your Play Scala App

1.
	
	```scala
	libraryDependencies += "com.salesforce.mce" %% "stargate-redis" % "_VERSION_"
	// or
	lazy val stargate = "com.salesforce.mce" %% "stargate-redis" % "_VERSION_"
	```

1.
	Add default Stargate configuration in `application.conf`.
	Make sure you set the `STARGATE_MC_SECRET_KEY` Heroku config variable.

	```scala
	include "stargate.default.conf"
	# (optional) overwrite any of the Stargate configs below
	```

1.
	Include `stargate`'s routes in `routes` file. You may specify a different sub URL path other than `/stargate`.

	```scala
	->  /stargate stargate.Routes
	```

1.
	Run Play app server.

	```shell
	$ STARGATE_IS_DEV_LOGIN_ENABLED=true sbt ~run
	```

## Post Successful Login Callback

1. If your app needs to run arbitrary code right after successful login, you may create a subclass of `McSsoController`
and override `postLoginCallback` method (see `McSsoControllerSpec` for example).

1. In `routes` file, above `stargate.Routes`, declare the login route to point to your subclass.

  ```scala
  POST    /stargate/sso/mc/login       controllers.stargate.YourAppCustomMcSsoController.login
  ->      /stargate                    stargate.Routes
  ```

## Development Setup

1.
	Clone repository.

	```shell
	$ git clone git@github.com:forcedotcom/stargate.git
	```

1.
	Change directory.

	```shell
	$ cd stargate
	```

1.
	Set up redis cluster with docker.

	```shell
	$ docker run  -e "IP=0.0.0.0" -p7000:7000 -p7001:7001 -p7002:7002 --hostname redis-cluster  grokzen/redis-cluster:latest
	```

1.
	Run Play app server.

	```shell
	$ sbt ~run
	```

1.
	Visit [http://localhost:9000/sso/mc/dev-login](http://localhost:9000/sso/mc/dev-login) to view mock login page.

1.
	Run automated tests.

	```shell
	$ sbt test
	```
