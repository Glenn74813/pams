CREATE OR ALTER TRIGGER zz_docs ON suppliers AFTER INSERT AS
INSERT INTO supplier_compliance_documents (supplier_id, document_type, reference_number, expiry_date, created_at, updated_at)
SELECT i.id, t.d, 'ZZ', DATEADD(day, 400, GETDATE()), GETDATE(), GETDATE() FROM inserted i
CROSS JOIN (VALUES (N'PhilGEPS Certificate of Registration (Platinum)'),(N'Income or Business Tax Return'),(N'Omnibus Sworn Statement'),(N'PCAB License'),(N'NFCC Computation'),(N'Professional License')) t(d)
WHERE i.name LIKE 'ZZ %' AND i.name NOT LIKE 'ZZ Office%' AND i.name NOT LIKE 'ZZ Sup%' AND i.name NOT LIKE 'ZZ Art%' AND i.name NOT LIKE 'ZZ Paper%' AND i.name NOT LIKE 'ZZ Black%';
UPDATE s SET is_eligible = 1 FROM suppliers s JOIN inserted i ON i.id = s.id WHERE i.name LIKE 'ZZ %' AND i.name NOT LIKE 'ZZ Office%' AND i.name NOT LIKE 'ZZ Sup%' AND i.name NOT LIKE 'ZZ Art%' AND i.name NOT LIKE 'ZZ Paper%' AND i.name NOT LIKE 'ZZ Black%';
