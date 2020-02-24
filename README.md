# Stargate

[![Maven Central](https://img.shields.io/maven-central/v/com.salesforce.mce/stargate-redis_2.12.svg?colorB=blue)](https://search.maven.org/search?q=a:stargate-redis_2.12)
[![CircleCI](https://circleci.com/gh/forcedotcom/stargate.svg?style=svg)](https://circleci.com/gh/forcedotcom/stargate)

> "It was a small outpost: a nexus, assimilator, and a couple of gateways." &mdash; <cite>Flight Sergeant Aaron Keebler</cite>.
> The stargate is an advanced protoss structure.

Stargate is a Play Scala Module that adds API endpoints to your app to enable integration with Salesforce Marketing Cloud Single Sign On. Redis is required to persist the session.

## How to Add Stargate to Your Play Scala App

Add dependency to `build.sbt`.
```scala
libraryDependencies += "com.salesforce.mce" %% "stargate-redis" % "_VERSION_"
```

Add default Stargate configuration in `application.conf`. Make sure to set `STARGATE_MC_SECRET_KEY` and `PLAY_SECRET`.
```scala
include "stargate.default.conf"
# (optional) overwrite any of the Stargate configs below
```

Include `stargate`'s routes in `routes` file. You may specify a different sub URL path other than `/stargate`.

```scala
->  /stargate stargate.Routes
```

Run Play app server.

```shell
$ STARGATE_IS_DEV_LOGIN_ENABLED=true sbt ~run
```

## Post Successful Login Callback

If your app needs to run arbitrary code right after successful login,
you may create a subclass of `McSsoController` and override `postLoginCallback` method
(see `McSsoControllerSpec` for example).

In `routes` file, above `stargate.Routes`, declare the login route to point to your subclass.

```scala
POST    /stargate/sso/mc/login       controllers.stargate.YourAppCustomMcSsoController.login
->      /stargate                    stargate.Routes
```

## Development Setup

Clone repository.

```shell
$ git clone git@github.com:forcedotcom/stargate.git
```

Change directory.

```shell
$ cd stargate
```

Set up redis cluster with docker.

```shell
$ docker run  -e "IP=0.0.0.0" -p7000:7000 -p7001:7001 -p7002:7002 --hostname redis-cluster  grokzen/redis-cluster:latest
```

Run Play app server in the redis module.

```shell
$ sbt ~redis/run
```

Visit [http://localhost:9000/sso/mc/dev-login](http://localhost:9000/sso/mc/dev-login) to view mock login page.

Run automated tests.

```shell
$ sbt test
```

## Credits

Special thanks to the following authors (in alphabetical order) for their commits in the original repository
before Stargate became open source.

* Andrew Hoblitzell
* Byju Sukumaran
* Calvin Henry
* Kexin Xie
* Patrick Frampton
* Sheng-Loong Su
* Trent Albright
