-- PayPal wurde als Zahlungsart ergaenzt (PaymentMethod.PAYPAL, shift.paypal_sales_cents in V3),
-- der Check-Constraint aus V1 liess aber weiterhin nur BAR und KARTE zu. Jeder PayPal-Verkauf
-- scheiterte deshalb an sale_method_check.
alter table sale drop constraint if exists sale_method_check;
alter table sale add constraint sale_method_check check (method in ('BAR', 'KARTE', 'PAYPAL'));
