# ﬥ [lamed]

Clojure native-image Lambdas.

Clojure has multiple ways of targeting AWS Lambda. The simplest and most common
is to use JVM support directly. This performs fine except for the long warmup
times that might be unacceptable for applications that get called very rarely
(e.g. webhooks). Alternatively, you could use ClojureScript. That works, but
generally limits your library choice and may mean rewriting parts of your
application. This project provides a third option: compile your Clojure (JVM)
application to a custom Lambda runtime using GraalVM's native-image.

Sample app:
https://github.com/latacora/lamed-sample-app

## Installation

Download from http://example.com/FIXME.

## Usage

FIXME: explanation

Run the project directly:

    $ clj -m com.latacora.lamed

Run the project's tests (they'll fail until you edit them):

    $ clj -A:test

## Usage

### High level integration

You provide a fn, we call it with a Lambda event already parsed to a Clojure
map, you return a Lambda response map, we translate it back into Lambda.

### Wrapping a [RequestStreamHandler][rsh]

This is a convenience method for wrapping existing Lambda implementations.

[rsh]: https://github.com/aws/aws-lambda-java-libs/blob/master/aws-lambda-java-core/src/main/java/com/amazonaws/services/lambda/runtime/RequestStreamHandler.java

## License

Copyright © Latacora, LLC

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
