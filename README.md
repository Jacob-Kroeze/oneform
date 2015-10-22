# oneform
[demo](http://oneformacc.herokuapp.com/)

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

1. Install postgres 9.4+

[postgreSQL][2]
[2]: http://www.postgresql.org/download/

2. Install leiningen

[leiningen][3]
[3]: https://github.com/technomancy/leiningen

    $ git clone https://github.com/Jacob-Kroeze/oneform.git

3. Create a file call profiles.clj in the root the project with this content editing for your database name

    {:profiles/dev
     {:env
      {:database-url
       "jdbc:postgresql://localhost/{databasename?user=db_user&password=db_password"}
       :app-context "/oneform"}}
       :profiles/test
     {:env
      {:database-url
       "jdbc:postgresql://localhost/myapp_test?user=db_user&password=db_password"}}}

4. Create the database names above. Try the command or use the ui [PGadmin][4]

[4]: http://www.pgadmin.org/download/
    
    $createdb databasename

5. Run
    $ lein run migrate
    $ lein run

6. Go to localhost:3000/oneform/form-builder/new in your browser

click save

7. Go to localhost:3000/oneform/form-schemas-index

Explore!

## Running

To start a web server for the application, run:

    lein run migrate
    lein run

## License

Copyright © 2015 Jacob Kroeze
