# oneform

One form depends on a database with it's own migration management and
named functions for create and read access. For us,
this project is called bxt-migrations internally.

Oneform also depends on [JSONschema](http://jsonschema.org) and
[alpacajs](http://alpacajs.org) for form management and generation.

We use a separate project for documenting, managing, and testing these
schemas, and Oneform provides a mechanize for a GUI to create (TODO)
temporary or very simple schema/forms.

Complex schemas should be loaded to the schemas table by the
dba/developer within the migrations project (bxt-migrations), and effects of a schema change should be tested.

Oneform is the form display, api and database access layer, and
miscelaneous tie stuff together, "Glue", layer. This, all for a form
and data management system that can flexibly be applied to many
enterprisey problems in the HR and Contract management domain.

Nice sales pitch, huh :)

## Prerequisites

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Install

`$ export DATABASE_URL=jdbc:postgresql:baxtore2_development`

TODO

## Running

To start a web server for the application, run:

    lein ring server

## License

Copyright © 2015 Jacob Kroeze
