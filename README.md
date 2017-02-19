# mailstats

... very much in progress!

Super-simple web app to get some basic statistics from a Gmail mailbox, after oauth.

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server-headless

Go to `http://localhost:3000` and authorize the Gmail account.