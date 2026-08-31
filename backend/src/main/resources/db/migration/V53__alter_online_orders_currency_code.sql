ALTER TABLE stores ALTER COLUMN currency_code TYPE VARCHAR(3);
ALTER TABLE customer_credits ALTER COLUMN currency_code TYPE VARCHAR(3);
ALTER TABLE sales ALTER COLUMN currency_code TYPE VARCHAR(3);
ALTER TABLE sale_returns ALTER COLUMN currency_code TYPE VARCHAR(3);
ALTER TABLE quotations ALTER COLUMN currency_code TYPE VARCHAR(3);
ALTER TABLE online_orders ALTER COLUMN currency_code TYPE VARCHAR(3);

