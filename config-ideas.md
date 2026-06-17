## Statement Trigger Via Configuration JSON

2 Types of Syntaxes:

Target Syntax: array of elements that work kind of like jsonpath and interrogate a JSON-esque data structure either through string keys or array indexes, e.g.
["key1", 0, "key2"]

would equate to "thing" on the following object:

{
    "key1": [
        {"key2": "thing"}
    ]
}

Criteria Syntax: an array of three elements, target syntax for field, operator (=, <, >, >=, <=, !=), and value. Value can also be an injection syntax under the right circumstances
[[Target Syntax], "=", "thing"]

Additionally both sides of a criteria might be themselves criteria syntax, resulting in T/F results. The operators in those cases should be (and, or)


Injection comes in the statement portion of a trigger, it will be an inline array that will reference values in one of three ways:
Examples of 2 types of injection (the first element is a keyword that identifies the type):

["this", [[Target Syntax]]] //this references the trigger entity

["query", "[class]", "[attribute]", [criteria syntax]] //this is an inline cache query 

for the third one, the criteria syntax 3rd parameter, the value, can ALSO be a ["this"] style injection, so you could have:

["query", "Car", ["color"], [["carId"], "=", ["this", ["relatedCarId"]]]]

As stated previously we can also have both sides be criteria and the operators be boolean logic

["query", "Car", ["carColor"], [
    [["carId"], "=", ["this", ["relatedCarId"]]],
    "or",
    [["carName"], "=", "Main Car"],
    ]
]

Actual input config:

```
{
    "statementTriggers": [
        {
            "type": "Interaction",
            "class": "LoadScenario",
            "criteria": [["key1"], "=", "thing1"] //criteria syntax
            "statement": {
                "actor": {
                    "objectType": "Agent",
                    "name": ["this", ["ScenarioName"]], //injection example
                    "account": {
                        "homePage": "https://qa.empact.io",
                        "name": ["Object", "Player", ["Name"], ["Number", "=", 0]]
                    }
                },
                "context": {
                    "extensions": {
                        "http://www.extensions.com/car-color": ["query", "Car", ["carColor"], [["carId"], "=", 4]]
                    }
                }
                ...
            }
        },
        { //same injection and criteria stuff, just a different type of trigger
            "type": "ObjectUpdate",
            "class": "Car",
            "criteria": [Criteria Syntax]
            "statement": {
                actor:...
            }
        }
    ],
    "lrs": {
        "host": "string"
        "key": "string"
        "secret": "secret"
        "batch": 4
    }    
}
```
