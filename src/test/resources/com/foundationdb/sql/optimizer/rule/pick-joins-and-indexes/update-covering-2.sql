DELETE FROM customers c1 WHERE c1.cid IN (SELECT c2.cid FROM customers c2 WHERE name = 'James')