
--
-- Overviews: function to create tables, validation, validation store,
-- finals (lazy) and at function to manage which of a document source is most
-- recent give a date.

create extension "uuid-ossp";
--;;
-- function for idempotent add_column
CREATE OR REPLACE function f_add_column(
        _tbl regclass, _col  text, _type regtype, OUT success bool)
    LANGUAGE plpgsql AS
    $func$
    BEGIN
    IF EXISTS (
    SELECT 1 FROM pg_attribute
    WHERE  attrelid = _tbl
        AND    attname = _col
        AND    NOT attisdropped) THEN
    success := FALSE;
    ELSE
    EXECUTE '
   ALTER TABLE ' || _tbl || ' ADD COLUMN ' || quote_ident(_col) || ' ' || _type;
    success := TRUE;
    END IF;
    END;
    $func$;

--;;
-- name: create_document_table(varchar, jsonb)
--
create or replace function create_document_table(_name varchar)
returns varchar
as $$
declare tablename varchar;
begin
select into tablename  (select to_regclass(_name::cstring));
if tablename is null then
  execute format(
  'create table %s (
   uuid uuid primary key unique,
   schema_uuid uuid,
   data jsonb not null,
   repo_data jsonb,
   search tsvector,
   created_at timestamptz default now() not null,
   updated_at timestamptz default now() not null)', _name);
  execute 'create index idx_'||_name||' on '||_name||' using GIN(body jsonb_path_ops)';
  execute 'create index idx_'||_name||'_search on '||_name||' using GIN(search)';
  select into tablename (select to_regclass(_name::cstring));
  return tablename;
else
  return null;
end if;
end $$ language plpgsql;
--
--select create_document_table('test_name3');
--drop table test_name3;

--;;
--name: schemas
    --
create table if not exists schemas (
        uuid uuid primary key,
        table_name varchar,
        version varchar,
        data json,
        created_at timestamptz,
        updated_at timestamptz );
--;;
create table if not exists form_schemas
    (uuid uuid primary key,
        schema_uuid uuid,
        data jsonb,
        created_at timestamptz default now(),
        table_name varchar(255)
        );

--;;
-- name: save_document_schema(jsonb)
-- used in create_document_table. Save in schemas, with a table_name
-- and uuid
create or replace function save_document_schema(_schema jsonb, _table_name varchar)
returns jsonb
as $$
with
text as (select uuid_generate_v5( uuid_nil(), _schema::text) uuid
), s as (
select data from form_schemas 
join text on form_schemas.uuid = text.uuid
)
insert into form_schemas (data, table_name, uuid, schema_uuid)
    values (_schema, _table_name, (select uuid from text), (select uuid from text))
 returning data::jsonb;
$$ language sql;
--test
--select save_document_schema('{"name":"type"}', 'test');
--delete from form_schemas where  table_name = 'test';

--;;
-- name: save_document(_table varchar, _data jsonb)
-- overloaded save_document(_table varchar, _data json)
--
--drop function save_document(varchar, jsonb);

create or replace function save_document(_table varchar, _data jsonb, OUT result jsonb)
as $$
declare _uuid uuid;
declare _schema_uuid uuid;
declare existing_data jsonb;
begin
perform( select create_document_table(_table) );
_schema_uuid = (select uuid from form_schemas where table_name =  _table order by created_at desc limit 1);
if _schema_uuid is null then raise notice 'Schema uuid cannot be null';
end if;
_uuid = uuid_generate_v5( uuid_nil(), _data::jsonb::text);
execute format('select data from %s where uuid = %s',
    _table,
    quote_literal(_uuid)) into existing_data;
if existing_data is not null then
result = existing_data;
else
execute format(
    'insert into %s (uuid, schema_uuid, data) values (%s, %s, %s) returning data',
    _table,
    quote_literal(_uuid),
    quote_literal(_schema_uuid),
    quote_literal(_data)
    ) into result;
end if;
end;
$$ language plpgsql;
--test
--select save_document('tdata', '{"bob":"is"}');

-- find doucment by id, by value, by search

--;;
--name: find_document(_table)
    --
create or replace function find_document(_table varchar, _uuid uuid, out result jsonb)
    as $$
-- find by uuid of row
    begin
    execute format(
        'select to_json(q)::jsonb 
         from (select uuid, schema_uuid, data from %s where uuid=%L limit 1) q',
        $1,
        $2)
into result;
    end;
    $$ language plpgsql;
--test
--select * from find_document('no_name_demo', 'edcf3bd1-2181-5b7c-8778-462079d91cfa'::uuid);

--;;
--name: find_document(_table, uuid)
    --
create or replace function find_document(_table varchar,
        criteria varchar default null,
        orderby varchar default 'created_at desc',
        out result jsonb)
    as $$
-- find by uuid of row
    begin
    execute format('select to_json(q)::jsonb 
            from (select uuid, schema_uuid, data from %s where data @> %s 
            order by %s limit 1) q',
            $1,
            quote_literal($2),
            $3)
    into result;
    end;
    $$ language plpgsql;
-- select * from find_document('no_name_demo', '{"check": true}'); 

--;;
--name: filter_documents(varchar, varchar, varchar)
--
create or replace function filter_documents(
        _table varchar,
        criteria varchar default null,
        orderby varchar default 'created_at desc')
    returns table (set jsonb)
    as $$
    begin
    if criteria = '' then
    return query execute format('select to_json(q)::jsonb
           from (select uuid, schema_uuid, data from %s where data @> %s 
                order by %L ) q',
        $1,
        coalesc($2, null::varchar),
        $3);
    else
    return query execute format('select to_json(q)::jsonb
           from (select uuid, schema_uuid, data from %s
                order by %s ) q',
        $1,
        $3);
    end if;
    end;
    $$ language plpgsql;
--select * from filter_documents('no_name_demo', '{"check": true}');
--select * from filter_documents('no_name_demo');

--;;
--name: search_documents(varchar,varchar,varchar)
--
create or replace function search_documents(
        _table varchar,
        _query varchar)
    returns table (set jsonb, rank real)
    as $$
    begin
    return query execute format(
        'select data, ts_rank_cd(search, to_tsquery(%s)) as rank 
        from %s 
        where search @@ to_tsquery(%s) 
        order by rank desc;',
        quote_literal($2),
        $1,
        quote_literal($2));
    end;
    $$ language plpgsql;

--select * from search_documents('no_name_demo', 'gray');

--;;
--name: update_search(varchar, uuid)
--

create or replace function update_search(_table varchar, _uuid uuid)
returns boolean
   as $$
    declare search_vals tsvector;
    declare vals jsonb;
    declare search_keys text[];
    begin
    search_keys = '{"fname", "email", "usc_id", "employee_id",
            "first_name", "last_name", "description", 
            "title", "street", "city", "state", "zip"}';
    execute format (
        'select data from %I where uuid = %L',
        _table,
        _uuid
        ) into vals;
        raise notice '%s', vals;
        search_vals =
        (select array_agg(r.value) from jsonb_each(vals) r
        where r.key = any (search_keys));
     execute format (
         'update %I set search = to_tsvector(%L) where uuid = %L',
         _table,
         search_vals,
         _uuid
         );
     return true;
    end;
    $$ language plpgsql;
--select update_search('no_name_demo', uuid) from no_name_demo;

--;;
--name: save_document(varchar, jsonb)
--
create or replace function save_document(_table varchar, _data jsonb, OUT result jsonb)
as $$
declare _uuid uuid;
declare _schema_uuid uuid;
declare existing_data jsonb;
begin
perform( select create_document_table(_table) );
_schema_uuid = (select uuid from form_schemas where table_name =  _table order by created_at desc limit 1);
if _schema_uuid is null then raise notice 'Schema uuid is null';
end if;
_uuid = uuid_generate_v5( uuid_nil(), _data::jsonb::text);
execute format('select data from %I where uuid = %L',
    _table,
    _uuid) into existing_data;
if existing_data is not null then
result = existing_data;
else
execute format(
    'insert into %I (uuid, schema_uuid, data) values (%L, %L, %L) returning data',
    _table,
    _uuid,
    _schema_uuid,
    _data) into result;
perform update_search(_table, _uuid);
end if;
end;
$$ language plpgsql;
--test

--select save_document('tdata', '{"bob":"great is"}');

--;;
-- add a column for schema uuid
select f_add_column('form_schemas', 'schema_uuid', 'uuid);
