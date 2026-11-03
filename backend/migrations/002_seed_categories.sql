-- Top categories follow what Ugandans buy and search most (Jumia Uganda order mix,
-- Jiji top searches). Re-rank later from Liso's own search logs.
-- Luganda names are DRAFTS until the native translator signs off the glossary.

INSERT INTO categories (id, parent_id, depth, name_en, name_lg, rank) VALUES
  ('phones',      NULL, 1, 'Phones & tablets', 'Amasimu',               10),
  ('home-care',   NULL, 1, 'Home care',        'Eby''okulabirira awaka', 20),
  ('computing',   NULL, 1, 'Computers',        'Kompyuta',              30),
  ('food',        NULL, 1, 'Food & groceries', 'Emmere',                35),
  ('appliances',  NULL, 1, 'Appliances',       'Ebyuma by''awaka',      40),
  ('clothes',     NULL, 1, 'Clothes',          'Engoye',                50),
  ('shoes',       NULL, 1, 'Shoes',            'Engatto',               60),
  ('cars',        NULL, 1, 'Cars',             'Mmotoka',               70),
  ('houses',      NULL, 1, 'Houses & rentals', 'Ennyumba',              80),
  ('services',    NULL, 1, 'Services',         'Obuweereza',            90),
  ('clothes-men',   'clothes', 2, 'Men',   'Abasajja', 10),
  ('clothes-women', 'clothes', 2, 'Women', 'Abakazi',  20),
  ('clothes-men-trousers', 'clothes-men', 3, 'Trousers', 'Empale', 10),
  ('phones-smartphones', 'phones', 2, 'Smartphones', 'Amasimu ga ssimu ez''omulembe', 10),
  ('phones-accessories', 'phones', 2, 'Accessories', 'Ebikozesebwa', 20);
