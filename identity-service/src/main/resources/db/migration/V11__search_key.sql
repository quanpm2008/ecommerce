-- P-37 (19/11/2026), AC-IDN-006: shop names are searched without Vietnamese diacritics and case. Both the stored name
-- and the query go through search_key(): Unicode NFC, every Vietnamese accented vowel (either case) to its base letter,
-- đ/Đ to d, then lowercase. A plain function in Identity's schema rather than the unaccent extension, which is
-- database-wide and might live in another service's schema; translating both cases first keeps it independent of the
-- database's LC_CTYPE. The stored and displayed name never changes. (No index yet: OQ-006.)
CREATE FUNCTION search_key(value TEXT) RETURNS TEXT LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT lower(translate(normalize(value, NFC),
        'àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ',
        'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyydAAAAAAAAAAAAAAAAAEEEEEEEEEEEIIIIIOOOOOOOOOOOOOOOOOUUUUUUUUUUUYYYYYD'))
$$;
