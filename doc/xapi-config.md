## HLA xAPI Adapter xAPI Configuration Reference

The xAPI configuration file controls which HLA events become xAPI statements, how statement templates are filled, how cached HLA object state is queried, and where generated statements are posted.

By default the adapter reads `config/xapi-config.json`. Set `XAPI_CONFIG` to load a different file.

```shell
XAPI_CONFIG=/path/to/xapi-config.json make run-dev
```

At the top level the file supports:

```json
{
  "statementTriggers": [],
  "lrs": {},
  "objectCache": {}
}
```

## Statement Triggers

`statementTriggers` is an array of templates that are processed when matching HLA events arrive.

```json
{
  "type": "Interaction",
  "class": "EntityAte",
  "criteria": [["PredatorId"], "!=", ["PreyId"]],
  "lookups": {
    "predator": {
      "class": "SimEntity",
      "criteria": [["EntityId"], "=", ["this", ["PredatorId"]]]
    }
  },
  "statement": {
    "actor": {
      "objectType": "Agent",
      "name": ["lookup", "predator", ["FirstName"]]
    },
    "verb": {
      "id": "http://example.com/verbs/ate",
      "display": {"en-US": "Ate"}
    },
    "object": {
      "id": "http://example.com/activity/eating"
    }
  }
}
```

Fields:

- `type`: Type of trigger. `Interaction` is currently wired into RTI subscriptions and statement processing. `ObjectUpdate` is parsed by the config model but object updates currently feed the object cache rather than firing statement triggers directly.
- `class`: The local HLA interaction class name. For interactions this is matched against the final segment of the RTI interaction class name.
- `criteria`: **NOTE: Not Yet Implemented!** Parsed as a criteria expression, but not currently applied when deciding whether an incoming interaction should fire the trigger.
- `lookups`: Optional named cache lookups resolved once before the statement template is processed. These can be used to reduce the size and complexity of queries in the body of the statement config.
- `statement`: An xAPI statement template. Any JSON object accepted by the xAPI spec can be used here, with injection expressions inserted where dynamic values are needed.

If multiple interaction triggers match the same interaction class, each trigger is processed and each resulting statement is queued for the LRS.

## Targets

A target is a JSON array path into an HLA parameter or cached object attribute:

```json
["EntityId"]
["Position", "X"]
["LocationHistory", 0, "Y"]
```

String parts name parameters, attributes, or fixed-record fields. Integer parts index into arrays. For cached object values, paths are stored as FOM-derived keys such as `Position.X` and `LocationHistory[0].Y`; array index lookups can also match wildcard array metadata in the cache.

## Criteria Expressions

Criteria are JSON arrays:

```json
[leftExpression, "operator", rightExpression]
```

Supported comparison operators:

- `=`
- `!=`
- `<`
- `>`
- `<=`
- `>=`

The left or right side may be a target, primitive value, nested criterion, or a `this` expression.

```json
[["Hunger"], ">", 50]
[["EntityId"], "=", ["this", ["PredatorId"]]]
[["Position", "X"], "<=", ["this", ["ToPosition", "X"]]]
```

Logical expressions use `and` or `or` between criteria:

```json
[
  [["Hunger"], ">", 50],
  "and",
  [["Position", "X"], "<", 15]
]
```

Use a single logical operator at a given array level. If mixed `and`/`or` logic is needed, prefer nested expressions.

In cache queries, `=` compares numbers numerically when both sides are numeric; otherwise it uses normal equality. Ordered comparisons compare numbers numerically, comparable values of the same class directly, and otherwise fall back to string comparison.

## Statement Injections

An injection can appear as a whole JSON value:

```json
"name": ["this", ["EntityId"]]
```

or inside a JSON string using `<<...>>` with an escaped JSON injection array:

```json
"name": "Rabbit <<[\"this\", [\"EntityId\"]]>>"
```

Whole-value injections preserve the replacement's JSON type. Inline injections are always rendered as text inside the containing string.

All injection types accept an optional final options object:

```json
["this", ["OptionalField"], {"nullable": true}]
```

`nullable` only allows a resolved value of `null` to render as JSON `null` (or as the text `null` inline). It does not allow missing objects or missing values. A missing required injection aborts the statement; the trigger returns no xAPI statement.

### `this`

`this` reads a value from the current event context.

```json
["this", ["PredatorId"]]
["this", ["FromPosition", "X"]]
```

For interaction triggers, `this` reads the interaction parameter map and decodes the value using the FOM. It supports top-level parameters, fixed-record fields, and array elements. Object-update `this` contexts are present in the codebase but currently return a placeholder value rather than decoded object attributes.

### `query`

`query` searches the object cache for the first current object of a class that matches criteria, then returns one target value from that object.

```json
["query", "Rabbit", ["EntityId"], [["Hunger"], ">", 50]]
```

Arguments:

- Class name: HLA object class to search, using the local FOM class name.
- Target: cached value to return from the matched object.
- Criteria: expression evaluated against cached values for each current object.
- Options: optional `{"nullable": true}`.

Queries use the adapter's current object cache, not arbitrary SQL provided in the config. Removed objects are excluded. If more than one object matches, the first cached object is used.

`this` may be used inside query criteria. It is resolved from the triggering event before the cache query runs:

```json
[
  "query",
  "SimEntity",
  ["EntityType"],
  [["EntityId"], "=", ["this", ["PredatorId"]]]
]
```

### `lookup`

`lookup` reads a value from a named object resolved by the trigger's `lookups`
section.

```json
{
  "lookups": {
    "predator": {
      "class": "SimEntity",
      "criteria": [["EntityId"], "=", ["this", ["PredatorId"]]]
    },
    "prey": {
      "class": "SimEntity",
      "criteria": [["EntityId"], "=", ["this", ["PreyId"]]]
    }
  },
  "statement": {
    "actor": {
      "name": "<<[\"lookup\", \"predator\", [\"FirstName\"]]>> <<[\"lookup\", \"predator\", [\"LastName\"]]>>"
    },
    "object": {
      "name": "<<[\"lookup\", \"prey\", [\"FirstName\"]]>> <<[\"lookup\", \"prey\", [\"LastName\"]]>>"
    }
  }
}
```

A lookup definition contains:

- `class`: HLA object class to search in the cache.
- `criteria`: cache criteria used to select the object. `this` expressions are allowed and are resolved against the triggering event.

A lookup injection has this shape:

```json
["lookup", "predator", ["EntityId"]]
```

The alias must exist in the trigger's `lookups` map. Each lookup alias is resolved once before processing the statement template, and all `lookup` injections for that alias reuse the same cached object.

## Object Cache

The object cache stores the latest reflected values for subscribed HLA object attributes in SQLite. It is enabled when either:

- a statement template contains a `query` injection,
- a trigger defines `lookups` or uses `lookup` injections that reference cached object attributes, or
- `objectCache.trackedObjects` explicitly requests tracked attributes.

When enabled, the adapter subscribes to the top-level object attributes required by query targets, query criteria, lookup targets, lookup criteria, and explicit tracked objects. Use the `trackedObjects` array to force cacheing of simulation objects:

```json
{
  "objectCache": {
    "trackedObjects": [
      {"class": "Rabbit", "attributes": ["EntityId", "Hunger"]},
      {"class": "World", "allAttributes": true},
      {"class": "*", "allAttributes": true}
    ]
  }
}
```

Tracked object fields:

- `class`: Local HLA object class name. Use `*` with `allAttributes: true` to subscribe to all top-level attributes for every FOM object class with attributes.
- `attributes`: Top-level attribute names to subscribe to.
- `allAttributes`: When `true`, expands to all top-level attributes for the class.

The cache decodes reflected values using the FOM and stores both top-level values and flattened nested values for fixed records and arrays. For example, reflecting `Position` can make `Position`, `Position.X`, and `Position.Y` available to query and lookup targets.

By default the SQLite database is `hla-object-cache.sqlite` in the working directory. It can be changed with:

```shell
HLA_OBJECT_CACHE_DB=/path/to/cache.sqlite make run-dev
HLA_OBJECT_CACHE_JDBC_URL=jdbc:sqlite:/path/to/cache.sqlite make run-dev
```

The cache starts fresh on initialization: the current schema drops and recreates object and FOM metadata tables when the cache opens.

## LRS Configuration

The `lrs` section configures the xAPI client.

```json
{
  "lrs": {
    "host": "http://localhost:8080/xapi",
    "key": "my_key",
    "secret": "my_secret",
    "batch": 35,
    "maxRetries": 3
  }
}
```

Fields:

- `host`: LRS xAPI endpoint.
- `key`: LRS basic auth username or key.
- `secret`: LRS basic auth password or secret.
- `batch`: Statement batch size passed to the xAPI client and used as the adapter buffer size.
- `maxRetries`: Number of scheduled retry attempts before the current in-memory buffer is cleared after repeated LRS post failures.

The buffer flush interval is controlled by the Java/Spring property `xapi.buffer.clear-rate`, defaulting to `10000` milliseconds.

```shell
java -Dxapi.buffer.clear-rate=5000 ...
```

## Complete Example

```json
{
  "statementTriggers": [
    {
      "type": "Interaction",
      "class": "EntityAte",
      "lookups": {
        "predator": {
          "class": "SimEntity",
          "criteria": [["EntityId"], "=", ["this", ["PredatorId"]]]
        },
        "prey": {
          "class": "SimEntity",
          "criteria": [["EntityId"], "=", ["this", ["PreyId"]]]
        }
      },
      "statement": {
        "actor": {
          "objectType": "Agent",
          "name": "<<[\"lookup\", \"predator\", [\"FirstName\"]]>> <<[\"lookup\", \"predator\", [\"LastName\"]]>>",
          "account": {
            "homePage": "https://hla-federepl.example/entities",
            "name": ["this", ["PredatorId"]]
          }
        },
        "verb": {
          "id": "http://example.com/verbs/ate",
          "display": {"en-US": "Ate"}
        },
        "object": {
          "objectType": "Agent",
          "name": "<<[\"lookup\", \"prey\", [\"FirstName\"]]>> <<[\"lookup\", \"prey\", [\"LastName\"]]>>",
          "account": {
            "homePage": "https://hla-federepl.example/entities",
            "name": ["this", ["PreyId"]]
          }
        }
      }
    }
  ],
  "objectCache": {
    "trackedObjects": [
      {"class": "SimEntity", "allAttributes": true}
    ]
  },
  "lrs": {
    "host": "http://localhost:8080/xapi",
    "key": "my_key",
    "secret": "my_secret",
    "batch": 35,
    "maxRetries": 3
  }
}
```
