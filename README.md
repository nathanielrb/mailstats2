# mailstats

Super-simple, not-very-safe mini app to get some basic statistics from a Gmail mailbox, after Oauth.

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

To run, start a web server:

    lein ring server

and a browser will open at [http://localhost:3000](http://localhost:3000) where you will be invited to authorize a Gmail account.

## Notes

Users are assumed to be uniquely identified by email address.

Email bodies are not sent to the RDF store, as I've been testing with my private email.

RDF data is added to the `http://5.9.241.51:8890/sparql` endpoint in the named graph `<http://tenforce.example.com/nathaniel>`

There's very little error handling, so a lot can go wrong.