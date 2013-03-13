CREATE TABLE customers(
    cid INT NOT NULL,
    first_name varchar(32),
    last_name varchar(32),
    PRIMARY KEY(cid)
);

CREATE TABLE addresses
(
  aid int NOT NULL,
  cid int NOT NULL,
  state CHAR(2),
  city VARCHAR(100),
  PRIMARY KEY(aid),
  GROUPING FOREIGN KEY (cid) REFERENCES customers(cid)
);

CREATE TABLE orders(
    oid INT NOT NULL,
    cid INT,
    odate DATETIME,
    PRIMARY KEY(oid),
    GROUPING FOREIGN KEY(cid) REFERENCES customers(cid)
);

CREATE TABLE items(
    iid INT NOT NULL,
    oid INT,
    sku INT,
    PRIMARY KEY(iid),
    GROUPING FOREIGN KEY(oid) REFERENCES orders(oid)
);

CREATE PROCEDURE test_proc(IN x DOUBLE, IN y DOUBLE, OUT plus DOUBLE, OUT times DOUBLE) LANGUAGE javascript PARAMETER STYLE variables DYNAMIC RESULT SETS 1  AS $$
  plus = x + y;
  times = x * y;
  java.sql.DriverManager.getConnection("jdbc:default:connection").createStatement().executeQuery("SELECT first_name, COUNT(*) AS n FROM test.customers GROUP BY 1");
$$;

CREATE PROCEDURE test_json(IN json_in VARCHAR(4096), OUT json_out VARCHAR(4096)) LANGUAGE javascript PARAMETER STYLE json EXTERNAL NAME 'entry' AS $$
  function request(params, context) {
    // There is only an accessor for root classes (customers, not orders).
    // There is no accessor with primary key in extent (only in children).
    // There is no List-like access.
    // Iterator next() only works after hasNext().
    var cname = null;
    for (var c in Iterator(context.extent.customers)) {
      cname = c.firstName + " " + c.lastName;
      break;
    }
    var oids = params.oids;
    var result = { orders: [] };
    for (var i = 0; i < oids.length; i++) {
      var entry = { oid: oids[i], name: cname };
      result.orders.push(entry);
    }
    return result;
  }

  function entry(json_in, json_out) {
    var params = JSON.parse(json_in);
    var context = com.akiban.direct.Direct.getContext();
    var result = request(params, context);
    if (typeof result !== 'undefined') {
      json_out[0] = JSON.stringify(result);
    }
  }
$$;
