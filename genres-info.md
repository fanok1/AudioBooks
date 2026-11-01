# Genre lists of the 13 audiobook sites

Reference of the genre/category lists every site of the AudioBooks app (`Provider` enum) shows, for building a genre normalizer. Collected 2026-10-30 with `curl` from the site's genres page named in `Provider.java` (the adapter's `genres()` shows which markup is read); live where the page answered, otherwise the offline test fixture or the adapter's built-in list (marked in Method).
Genre names are exactly as the site writes them (Russian); Slug = URL path on the site (no host); Books = book count printed next to the genre (empty = site shows none); Parent = category/group the genre sits under (empty = top level).

## Summary

| # | Provider enum | Site title | Host | Genres URL | Method | Genre count | Hierarchical | Notes |
|---|---|---|---|---|---|---|---|---|
| 1 | AKNIGA | Akniga | akniga.org | https://akniga.org/sections/ | live | 31 (24 top-level) | yes | 24 sections + 7 labels as children of 2 of them |
| 2 | KNIGAVUHE | Книга в ухе | knigavuhe.org | https://knigavuhe.org/genres/ | live | 23 | no | mobile UA redirected to m.; 1 external-link row left out |
| 3 | IZIBUK | ИзиБук | izib.uk | https://izib.uk/genres?p=1 | live | 30 (23 top-level) | yes | mobile UA redirected to pda.; 7 sub-entries are menu "?subcategory=" links |
| 4 | ABMP3 | AudioBook-mp3 | audiobook-mp3.com | https://audiobook-mp3.com/genres | live | 23 | no | 2 external-site cards left out |
| 5 | BAZA_KNIG | База Книг | baza-knig.top | https://baza-knig.top/ | live | 36 | no | home page left menu; flat; no counts |
| 6 | KNIGOBLUD | Книгоблуд | www.knigoblud.club | https://www.knigoblud.club/genres | built-in list (code) | 22 | no | /genres is 404; list is hard-coded in the adapter; 8/22 spot-checked live |
| 7 | AUDIOBOO | Audioboo | audioboo.org | https://audioboo.org/ | fixture | 50 | no | Cloudflare challenge (HTTP 403) -> fixture dated 2026-10-24 |
| 8 | OTRUB | Отрубин | otrub.in | https://otrub.in/genres.html | live | 22 | no | mobile UA redirected to m. |
| 9 | POLEKNIG | Полекниг | poleknig.com | https://poleknig.com/genres | live | 80 | no | no counts; numeric ids |
| 10 | UKNIG | уКниг | uknig.com | https://uknig.com/genres | live | 120 in 9 groups | yes | 9 group headings as Parent |
| 11 | AUDIOKNIGI_FUN | Аудиокниги.fun | audioknigi.fun | https://audioknigi.fun/c/zhanr.html | live | 57 | no | equals fixture |
| 12 | AKNIG_XYZ | Aknig.xyz | s2.aknig.xyz | https://s2.aknig.xyz/genres.html | live | 35 | no | hidden "ban" category left out; no counts |
| 13 | AUDIOKNIGA_LIFE | Аудиокнига Life | audiokniga.life | https://audiokniga.life/ | live | 19 (16 top-level) | yes | subgenres nested by URL path; sidebar of home page |

Total rows: 548; distinct normalized names: 187.

## AKNIGA — Akniga (akniga.org)

Source: https://akniga.org/sections/ (live). 24 sections (the app's genre list) plus 7 finer labels (/label/genre/...) the page prints under 2 sections; Books = count shown for the section. Other sections list no labels.

| Genre (as on site) | Slug / path | Books | Parent |
|---|---|---|---|
| Фантастика, фэнтези | `/section/fantasy/` | 20552 |  |
| Фантастика | `/label/genre/fantastika/` |  | Фантастика, фэнтези |
| Фэнтези | `/label/genre/fentezi/` |  | Фантастика, фэнтези |
| Магический реализм | `/label/genre/magicheskiy-realizm/` |  | Фантастика, фэнтези |
| ЛитРПГ | `/label/genre/litrpg/` |  | Фантастика, фэнтези |
| Детективы, триллеры | `/section/detective/` | 8558 |  |
| Детектив | `/label/genre/detektiv/` |  | Детективы, триллеры |
| Триллер | `/label/genre/triller/` |  | Детективы, триллеры |
| Боевик | `/label/genre/boevik/` |  | Детективы, триллеры |
| Роман, проза | `/section/roman/` | 12761 |  |
| Ужасы, мистика | `/section/uzhasy_mistika/` | 8386 |  |
| Классика | `/section/classic/` | 5609 |  |
| Психология, философия | `/section/psihologiya/` | 1912 |  |
| Аудиоспектакли | `/section/audiospektakli/` | 1712 |  |
| Приключения | `/section/priklucheniya/` | 1714 |  |
| История | `/section/istoriya/` | 2058 |  |
| Ранобэ, новеллы | `/section/ranobe/` | 2197 |  |
| Научно-популярное | `/section/nauchno_populyarnye/` | 1647 |  |
| Юмор, сатира | `/section/humor/` | 2349 |  |
| Эзотерика | `/section/spirituality/` | 1032 |  |
| Бизнес | `/section/business/` | 907 |  |
| Обучение | `/section/obuchenie/` | 1252 |  |
| Аудиосказки и рассказы для детей | `/section/detskie/` | 4741 |  |
| Биографии, мемуары | `/section/biography/` | 1933 |  |
| Разное | `/section/raznoe/` | 1167 |  |
| Медицина, здоровье | `/section/health/` | 476 |  |
| На иностранных языках | `/section/inostrannie/` | 150 |  |
| Религия | `/section/religion/` | 878 |  |
| Путешествия | `/section/travel/` | 127 |  |
| Поэзия | `/section/poeziya/` | 1125 |  |
| Сказки | `/section/skazki/` | 1739 |  |

## KNIGAVUHE — Книга в ухе (knigavuhe.org)

Source: https://knigavuhe.org/genres/ (live). Mobile UA is redirected to m.knigavuhe.org (same 24 rows, same names and counts); table taken from the desktop page (2nd request, desktop UA). One row, "Школьная литература", links to the external site litravuhe.ru and is left out (the adapter would build a broken URL for it).

| Genre (as on site) | Slug / path | Books | Parent |
|---|---|---|---|
| Фантастика и фэнтези | `/genre/fantastika/` | 18597 |  |
| Детективы и триллеры | `/genre/detektivy-trillery/` | 7800 |  |
| Аудиоспектакли | `/genre/audiospektakli/` | 1720 |  |
| Бизнес | `/genre/biznes/` | 272 |  |
| Биографии и мемуары | `/genre/biografii-memuary/` | 1276 |  |
| Для детей и Аудиосказки | `/genre/dlja-detejj/` | 3502 |  |
| История | `/genre/istorija/` | 1017 |  |
| Классика | `/genre/klassika/` | 3251 |  |
| Медицина и здоровье | `/genre/medicina-zdorove/` | 146 |  |
| На иностранных языках | `/genre/na-inostrannykh-jazykakh/` | 95 |  |
| Научно-популярное | `/genre/nauchno-populjarnoe/` | 651 |  |
| Обучение | `/genre/obuchenie/` | 123 |  |
| Поэзия | `/genre/poehzija/` | 543 |  |
| Приключения, вестерны и боевики | `/genre/prikljuchenija/` | 1331 |  |
| Психология и философия | `/genre/psikhologija-filosofija/` | 989 |  |
| Разное | `/genre/raznoe/` | 973 |  |
| Ранобэ и новеллы | `/genre/ranobeh/` | 1603 |  |
| Религия | `/genre/religija/` | 461 |  |
| Роман и проза | `/genre/roman-proza/` | 11616 |  |
| Тру-крайм | `/genre/true-crime/` | 783 |  |
| Ужасы и мистика | `/genre/uzhasy-mistika/` | 9108 |  |
| Эзотерика | `/genre/ehzoterika/` | 773 |  |
| Юмор и сатира | `/genre/jumor-satira/` | 1428 |  |

## IZIBUK — ИзиБук (izib.uk)

Source: https://izib.uk/genres?p=1 (live). Mobile UA is redirected to pda.izib.uk; one page, no pagination. Slug /genre<N> is a numeric id. The 7 rows with a parent are the "?subcategory=" entries of the site menu (sub-lists of genre1), no counts; the top-level genres are the app's genre list.

| Genre (as on site) | Slug / path | Books | Parent |
|---|---|---|---|
| Фантастика, фэнтези | `/genre1` | 44855 |  |
| Детективы, триллеры, боевики | `/genre4` | 13850 |  |
| Аудиоспектакли, радиопостановки и литературные чтения | `/genre7` | 1454 |  |
| Бизнес, личностный рост | `/genre14` | 1090 |  |
| Биографии, мемуары, ЖЗЛ | `/genre22` | 2497 |  |
| Для детей, аудиосказки, стишки | `/genre6` | 5205 |  |
| История, культурология | `/genre12` | 2108 |  |
| Классика | `/genre13` | 6385 |  |
| Медицина, здоровье | `/genre21` | 576 |  |
| На иностранных языках | `/genre15` | 46 |  |
| Научно-популярное | `/genre11` | 1021 |  |
| Обучение | `/genre17` | 302 |  |
| Поэзия | `/genre18` | 601 |  |
| Приключения, военные приключения | `/genre8` | 2238 |  |
| Психология, философия | `/genre2` | 3044 |  |
| Разное | `/genre16` | 2799 |  |
| Ранобэ | `/genre20` | 1614 |  |
| Религия | `/genre19` | 492 |  |
| Роман, проза | `/genre3` | 22135 |  |
| Тру-крайм | `/genre24` | 73 |  |
| Ужасы, мистика, хоррор | `/genre10` | 7034 |  |
| Эзотерика, Нетрадиционные религиозно-философские учения | `/genre5` | 942 |  |
| Юмор, сатира | `/genre9` | 1111 |  |
| Любовное Фэнтези | `/genre1?subcategory=3` |  | Фантастика, фэнтези |
| ЛитРПГ | `/genre1?subcategory=2` |  | Фантастика, фэнтези |
| Попаданцы | `/genre1?subcategory=1` |  | Фантастика, фэнтези |
| S.T.A.L.K.E.R. | `/genre1?subcategory=4` |  | Фантастика, фэнтези |
| S-T-I-K-S | `/genre1?subcategory=5` |  | Фантастика, фэнтези |
| Метро 2033 | `/genre1?subcategory=7` |  | Фантастика, фэнтези |
| Этногенез | `/genre1?subcategory=6` |  | Фантастика, фэнтези |

## ABMP3 — AudioBook-mp3 (audiobook-mp3.com)

Source: https://audiobook-mp3.com/genres (live). Slug /genre-<N>-<name> carries a numeric id. The page also has 2 cards linking to other sites (babushkini-skazki.ru "Мультфильмы", chtivo.org.ua "Аудіоказки"): not genres, left out as the adapter does.

| Genre (as on site) | Slug / path | Books | Parent |
|---|---|---|---|
| Фантастика, фэнтези | `/genre-1-fantastika` | 17714 |  |
| Роман, проза | `/genre-5-roman-proza` | 10965 |  |
| Ужасы, мистика | `/genre-20-uzhasy-mistika` | 8733 |  |
| Детективы, триллеры | `/genre-12-detektivy-trillery` | 7482 |  |
| Для детей | `/genre-7-dlja-detejj` | 3030 |  |
| Классика | `/genre-3-klassika` | 2840 |  |
| Аудиоспектакли | `/genre-21-audiospektakli` | 1735 |  |
| Ранобэ | `/genre-2-ranobeh` | 1616 |  |
| Приключения | `/genre-6-prikljuchenija` | 1366 |  |
| Биографии, мемуары | `/genre-4-biografii-memuary` | 1227 |  |
| Юмор, сатира | `/genre-19-jumor-satira` | 1206 |  |
| История | `/genre-16-istorija` | 1002 |  |
| Психология, философия | `/genre-9-psikhologija-filosofija` | 943 |  |
| Разное | `/genre-22-raznoe` | 905 |  |
| Эзотерика | `/genre-10-ehzoterika` | 765 |  |
| Тру-крайм | `/genre-23-true-crime` | 713 |  |
| Научно-популярное | `/genre-14-nauchno-populjarnoe` | 590 |  |
| Поэзия | `/genre-8-poehzija` | 457 |  |
| Религия | `/genre-15-religija` | 445 |  |
| Бизнес | `/genre-18-biznes` | 266 |  |
| Медицина, здоровье | `/genre-13-medicina-zdorove` | 132 |  |
| Обучение | `/genre-11-obuchenie` | 115 |  |
| На иностранных языках | `/genre-17-na-inostrannykh-jazykakh` | 79 |  |

## BAZA_KNIG — База Книг (baza-knig.top)

Source: https://baza-knig.top/ (live). Genres = left menu "Категории" of the home page (ul.left-menu-items); no counts. The menu is flat: the last 9 entries (Фантастика, фэнтези ... Попаданцы) are themes/series listed after the main alphabet, in the site's order.

| Genre (as on site) | Slug / path | Books | Parent |
|---|---|---|---|
| Аудиоспектакль | `/audiospektakl/` |  |  |
| Альтернативная история | `/alternativnaya-istoriiya/` |  |  |
| Бизнес | `/biznes/` |  |  |
| Биографии, мемуары | `/biografiya/` |  |  |
| Боевики | `/boevik/` |  |  |
| Детективы, триллеры | `/detektivy-trillery/` |  |  |
| Для детей | `/dlya-detey/` |  |  |
| История | `/istoriya/` |  |  |
| Классика | `/klassika/` |  |  |
| Любовный роман | `/lyubovnyy-roman/` |  |  |
| Любовное фэнтези | `/lyubovnoe-fentezi/` |  |  |
| Медицина, здоровье | `/medicina-zdorove/` |  |  |
| Научно-популярное | `/nauchno-populyarnoe/` |  |  |
| Обучение | `/obuchenie/` |  |  |
| Познавательная литература | `/poznavatelnaya-literatura/` |  |  |
| Постапокалипсис | `/postapokalipsis/` |  |  |
| Поэзия | `/poeziya/` |  |  |
| Приключения | `/priklyucheniya/` |  |  |
| Психология, философия | `/psihologiya-filosofiya/` |  |  |
| Ранобэ | `/ranobe/` |  |  |
| Разное | `/raznoe/` |  |  |
| Религия | `/religiya/` |  |  |
| Роман, проза | `/roman-proza/` |  |  |
| Сказки | `/skazki/` |  |  |
| Ужасы, мистика | `/uzhasy-mistika/` |  |  |
| Эзотерика | `/ezoterika/` |  |  |
| Юмор, сатира | `/yumor-satira/` |  |  |
| Фантастика, фэнтези | `/fantastika-fentezii/` |  |  |
| S.T.A.L.K.E.R. | `/stalker/` |  |  |
| S-T-I-K-S | `/s-t-i-k-s/` |  |  |
| LitRPG | `/litrpg/` |  |  |
| Метро 2033 | `/metro-2033/` |  |  |
| Этногенез | `/etnogenez/` |  |  |
| Технотьма | `/tehnotima_/` |  |  |
| EVE online | `/eve-online/` |  |  |
| Попаданцы | `/popadanci/` |  |  |

## KNIGOBLUD — Книгоблуд (www.knigoblud.club)

Source: https://www.knigoblud.club/genres (built-in list (code)). Live /genres answers HTTP 404; the adapter never fetches genres, it ships this fixed list (Knigoblud.genreList). One more live request (home page, redirected to mob.knigoblud.club) showed 8 of the 22 entries with identical names and ids; the other 14 are not on the home page and are unverified. No counts. Slug = UUID.

| Genre (as on site) | Slug / path | Books | Parent |
|---|---|---|---|
| Фантастика, фэнтези | `/9d47c7aa-9e2e-4fe0-bcd5-14f7d4874198` |  |  |
| Детективы, триллеры, боевики | `/4daab267-3592-4604-9e34-8f3b6a1db9fe` |  |  |
| Аудиоспектакли, радиопостановки и литературные чтения | `/0efa0796-8734-4735-ac03-d4eca1ccb013` |  |  |
| Бизнес, личностный рост | `/8ea14af7-f600-4e30-a0eb-67b615cdb701` |  |  |
| Биографии, мемуары, ЖЗЛ | `/760dd554-482b-4817-8283-5b9ae9e76431` |  |  |
| Для детей, аудиосказки, стишки | `/5982eae9-3868-4593-a9b0-b3378370345f` |  |  |
| История, культурология | `/31d39d95-7bf5-420b-9db4-66c448785a07` |  |  |
| Классика | `/0c6d4928-555a-4671-99e9-9bcf9a37d382` |  |  |
| Медицина, здоровье | `/f00832de-609c-4cc8-9017-2571caa4bfb2` |  |  |
| На иностранных языках | `/fb8d64ce-902d-44aa-9c21-77cb85a5b418` |  |  |
| Научно-популярное | `/9da19d35-32ee-41e7-ab8c-5ec554fcdd8a` |  |  |
| Обучение | `/7445b383-418a-475a-8374-ef2822d0552c` |  |  |
| Поэзия | `/a1d4487a-12d6-4280-b1a2-4c204bd6908a` |  |  |
| Приключения, военные приключения | `/e7d0d5bc-210a-4fc6-9598-19baf47c9b13` |  |  |
| Психология, философия | `/09afc417-fa54-4b56-bb54-f950296a0b50` |  |  |
| Разное | `/697856ad-1d48-48d5-ad20-5f00cc169f75` |  |  |
| Ранобэ | `/07bc7998-8f32-49a1-8048-07a2c2294a11` |  |  |
| Религия | `/4c37cad9-8ee6-4ce7-b62e-b52ef717952c` |  |  |
| Роман, проза | `/61c37161-1899-4253-a4c3-9417f4b5c1c5` |  |  |
| Ужасы, мистика, хоррор | `/120a1664-12e5-4ac6-853f-792f6a8007e9` |  |  |
| Эзотерика, Нетрадиционные религиозно-философские учения | `/e4117eca-c027-4f80-8004-3851d9b562c7` |  |  |
| Юмор, сатира | `/70bb89e8-65f7-414d-b98d-107878734c89` |  |  |

## AUDIOBOO — Audioboo (audioboo.org)

Source: https://audioboo.org/ (fixture). Live request returned HTTP 403 with a Cloudflare challenge page ("Just a moment..."). Taken from the offline test fixture app/src/test/resources/fixtures/audioboo/listing-index-p1.html (committed 2026-10-24): the navigation menu (2 columns, ul.navmenu), Books = the [N] after each name. Registry URL is the paged index (/page/); the adapter reads the home page menu.

| Genre (as on site) | Slug / path | Books | Parent |
|---|---|---|---|
| Альтернативная история | `/altist/` | 953 |  |
| Античность | `/antichnost/` | 38 |  |
| Аудиоспектакль | `/audiospektakl/` | 2864 |  |
| Бизнес | `/biz/` | 824 |  |
| Биография | `/biogr/` | 3023 |  |
| Боевик | `/boevik/` | 1367 |  |
| Война | `/voina/` | 821 |  |
| Вселенная метро 2033 | `/metrovsel/` | 101 |  |
| Детектив | `/didiktiva/` | 12501 |  |
| Детская литература | `/detsklit/` | 3452 |  |
| Драма | `/drama/` | 894 |  |
| Интервью | `/interviu/` | 17 |  |
| История | `/istoria/` | 3585 |  |
| Классика | `/klassika/` | 6322 |  |
| Лекция | `/lekcia/` | 214 |  |
| ЛФФР | `/lffr/` | 11499 |  |
| Мемуары | `/memuari/` | 1990 |  |
| Медицина | `/medicina/` | 440 |  |
| Мистика | `/mistic/` | 8155 |  |
| Новелла | `/novella/` | 105 |  |
| Повесть | `/povest/` | 849 |  |
| Попаданцы | `/popadanci/` | 6877 |  |
| Познавательная литература | `/poznaem/` | 3078 |  |
| Постапокалипсис | `/postapakalipsis/` | 631 |  |
| Поэзия | `/poezia/` | 791 |  |
| Притча | `/pritch/` | 85 |  |
| Приключения | `/prikluchenia/` | 3327 |  |
| Проза | `/proza/` | 10819 |  |
| Психология | `/psihologia/` | 2617 |  |
| Публицистика | `/publicictika/` | 1475 |  |
| Ранобэ | `/ranobe/` | 1870 |  |
| Религия | `/rellign/` | 541 |  |
| Роман | `/roman/` | 14591 |  |
| Сказка | `/skazka/` | 2069 |  |
| Стихи | `/ssstihi/` | 184 |  |
| Триллер | `/triller/` | 3575 |  |
| Трєш | `/tresh/` | 16 |  |
| Ужасы | `/ugas/` | 6733 |  |
| Учебник | `/uchebnik/` | 63 |  |
| Фантастика | `/fantastika/` | 20788 |  |
| Философия | `/filosophi/` | 648 |  |
| Фэнтези | `/fenezi/` | 11248 |  |
| Хоррор | `/horror/` | 593 |  |
| Эзотерика | `/ezoterika/` | 841 |  |
| Эротика | `/erotika/` | 519 |  |
| Этногенез | `/entogenez/` | 54 |  |
| Юмор | `/umor/` | 2709 |  |
| LitRPG | `/litrpg/` | 1727 |  |
| Warhammer 40000 | `/warhammer-40000/` | 434 |  |
| S.T.A.L.K.E.R. | `/stalker/` | 401 |  |

## OTRUB — Отрубин (otrub.in)

Source: https://otrub.in/genres.html (live). Mobile UA is redirected to m.otrub.in (same list as the desktop fixture genres.html: 22 rows). Books = count in brackets.

| Genre (as on site) | Slug / path | Books | Parent |
|---|---|---|---|
| Фантастика, фэнтези | `/genres/fantastika.html` | 44855 |  |
| Детективы, триллеры, боевики | `/genres/detektivy-trillery.html` | 13850 |  |
| Аудиоспектакли, радиопостановки и литературные чтения | `/genres/audiospektakli.html` | 1454 |  |
| Бизнес, личностный рост | `/genres/biznes.html` | 1090 |  |
| Биографии, мемуары, ЖЗЛ | `/genres/biografii-memuary.html` | 2497 |  |
| Для детей, аудиосказки, стишки | `/genres/dlja-detejj.html` | 5205 |  |
| История, культурология | `/genres/istorija.html` | 2108 |  |
| Классика | `/genres/klassika.html` | 6385 |  |
| Медицина, здоровье | `/genres/medicina-zdorove.html` | 576 |  |
| На иностранных языках | `/genres/na-inostrannykh-jazykakh.html` | 46 |  |
| Научно-популярное | `/genres/nauchno-populjarnoe.html` | 1021 |  |
| Обучение | `/genres/obuchenie.html` | 302 |  |
| Поэзия | `/genres/poehzija.html` | 601 |  |
| Приключения, военные приключения | `/genres/prikljuchenija.html` | 2238 |  |
| Психология, философия | `/genres/psikhologija-filosofija.html` | 3044 |  |
| Разное | `/genres/raznoe.html` | 2799 |  |
| Ранобэ | `/genres/ranobeh.html` | 1614 |  |
| Религия | `/genres/religija.html` | 492 |  |
| Роман, проза | `/genres/roman-proza.html` | 22135 |  |
| Ужасы, мистика, хоррор | `/genres/uzhasy-mistika.html` | 7034 |  |
| Эзотерика, Нетрадиционные религиозно-философские учения | `/genres/ehzoterika.html` | 942 |  |
| Юмор, сатира | `/genres/jumor-satira.html` | 1111 |  |

## POLEKNIG — Полекниг (poleknig.com)

Source: https://poleknig.com/genres (live). Alphabetical, Latin names first; no counts. Slug is a numeric id. Several near-duplicates (Проза / Роман и проза / Романы, Детектив / Детективы, триллеры).

| Genre (as on site) | Slug / path | Books | Parent |
|---|---|---|---|
| EVE online | `/genres/51` |  |  |
| S-T-I-K-S | `/genres/35` |  |  |
| S.T.A.L.K.E.R. | `/genres/4` |  |  |
| Warhammer 40000 | `/genres/61` |  |  |
| Альтернативная история | `/genres/49` |  |  |
| Аудиоспектакль | `/genres/48` |  |  |
| Бизнес | `/genres/28` |  |  |
| Биографии, мемуары | `/genres/52` |  |  |
| Боевая фантастика | `/genres/113` |  |  |
| Боевик | `/genres/41` |  |  |
| Боевое фэнтези | `/genres/115` |  |  |
| Бояръ-аниме | `/genres/120` |  |  |
| Бытовое фэнтези | `/genres/117` |  |  |
| Война | `/genres/26` |  |  |
| Вселенная метро 2033 | `/genres/7` |  |  |
| Героическое фэнтези | `/genres/116` |  |  |
| Городское фэнтези | `/genres/107` |  |  |
| Детектив | `/genres/8` |  |  |
| Детективное фэнтези | `/genres/108` |  |  |
| Детективы, триллеры | `/genres/40` |  |  |
| Для детей, Аудиосказки | `/genres/63` |  |  |
| Дозоры | `/genres/62` |  |  |
| Драма | `/genres/17` |  |  |
| Историческая проза | `/genres/71` |  |  |
| Историческая фантастика | `/genres/114` |  |  |
| Исторический любовный роман | `/genres/112` |  |  |
| История | `/genres/18` |  |  |
| Классика | `/genres/12` |  |  |
| ЛитРПГ | `/genres/66` |  |  |
| Любовное фэнтези | `/genres/59` |  |  |
| Любовный роман | `/genres/54` |  |  |
| Магический реализм | `/genres/70` |  |  |
| Медицина, здоровье | `/genres/57` |  |  |
| Мистика | `/genres/6` |  |  |
| На иностранных языках | `/genres/60` |  |  |
| Научно-популярное | `/genres/50` |  |  |
| Обучение | `/genres/58` |  |  |
| Повести | `/genres/101` |  |  |
| Повести и рассказы | `/genres/23` |  |  |
| Познавательная литература | `/genres/37` |  |  |
| Попаданцы | `/genres/16` |  |  |
| Постапокалипсис | `/genres/5` |  |  |
| Постмодернизм | `/genres/69` |  |  |
| Поэзия | `/genres/55` |  |  |
| Приключения | `/genres/15` |  |  |
| Приключения и вестерн | `/genres/131` |  |  |
| Приключения, вестерны и боевики | `/genres/132` |  |  |
| Приключения, военные приключения | `/genres/105` |  |  |
| Приключенческое фэнтези | `/genres/109` |  |  |
| Проза | `/genres/13` |  |  |
| Психоделика | `/genres/65` |  |  |
| Психология | `/genres/20` |  |  |
| Психология, философия | `/genres/53` |  |  |
| Радиоспектакли | `/genres/10` |  |  |
| Разное | `/genres/38` |  |  |
| Ранобэ, новеллы | `/genres/103` |  |  |
| Рассказы | `/genres/102` |  |  |
| Реализм | `/genres/72` |  |  |
| Религия | `/genres/56` |  |  |
| Роман и проза | `/genres/121` |  |  |
| Романтическое фэнтези | `/genres/110` |  |  |
| Романы | `/genres/14` |  |  |
| Сказки | `/genres/25` |  |  |
| Стихи | `/genres/34` |  |  |
| Сюрреализм | `/genres/67` |  |  |
| Технотьма | `/genres/47` |  |  |
| Триллер | `/genres/9` |  |  |
| Тру-крайм | `/genres/130` |  |  |
| Ужасы, мистика | `/genres/43` |  |  |
| Учебник | `/genres/31` |  |  |
| Фантастика | `/genres/1` |  |  |
| Фантастика, фэнтези | `/genres/104` |  |  |
| Философия | `/genres/21` |  |  |
| Фэнтези | `/genres/2` |  |  |
| Хоррор/Ужасы | `/genres/64` |  |  |
| Эзотерика | `/genres/29` |  |  |
| Эротика | `/genres/118` |  |  |
| Эротическое фэнтези | `/genres/111` |  |  |
| Этногенез | `/genres/19` |  |  |
| Юмор | `/genres/11` |  |  |

## UKNIG — уКниг (uknig.com)

Source: https://uknig.com/genres (live). Genres are grouped under 9 headings (div.header), shown in Parent; the headings themselves are not links. The group "Фантастика" also contains a genre "Фантастика". Books = count shown. Live page equals the offline fixture genres.html.

| Genre (as on site) | Slug / path | Books | Parent |
|---|---|---|---|
| Для детей | `/genres/dlya-detej` | 4766 | Детские книги |
| Сказки | `/genres/skazki` | 3139 | Детские книги |
| Бизнес | `/genres/biznes` | 760 | Знания и навыки |
| Биографии, мемуары | `/genres/biografii-memuary` | 3798 | Знания и навыки |
| Высокие технологии | `/genres/vysokie-tehnologii` | 4 | Знания и навыки |
| Документальная литература | `/genres/dokumentalnaya-literatura` | 675 | Знания и навыки |
| История | `/genres/istoriya` | 2852 | Знания и навыки |
| Личные финансы | `/genres/lichnye-finansy` | 73 | Знания и навыки |
| Медицина, здоровье | `/genres/meditsina-zdorove` | 526 | Знания и навыки |
| Научно-популярное | `/genres/nauchno-populyarnoe` | 1477 | Знания и навыки |
| Обучение | `/genres/obuchenie` | 288 | Знания и навыки |
| Ораторское искусство, риторика | `/genres/oratorskoye-iskusstvo` | 57 | Знания и навыки |
| Познавательная литература | `/genres/poznavatelnaya-literatura` | 2523 | Знания и навыки |
| Психология | `/genres/psihologiya` | 3791 | Знания и навыки |
| Психология, философия | `/genres/psihologiya-filosofiya` | 1777 | Знания и навыки |
| Публицистика | `/genres/publicistika` | 909 | Знания и навыки |
| Религия | `/genres/religiya` | 962 | Знания и навыки |
| Саморазвитие | `/genres/samorazvitie` | 1784 | Знания и навыки |
| Семейная психология | `/genres/semeynaya-psihologiya` | 218 | Знания и навыки |
| Социальная психология | `/genres/socialnaya-psihologiya` | 369 | Знания и навыки |
| Учебник | `/genres/uchebnik` | 93 | Знания и навыки |
| Философия | `/genres/filosofiya` | 1010 | Знания и навыки |
| Эзотерика | `/genres/ezoterika` | 1324 | Знания и навыки |
| Экономика | `/genres/ekonomika` | 58 | Знания и навыки |
| Этногенез | `/genres/etnogenez` | 63 | Знания и навыки |
| Зарубежный любовный роман | `/genres/zarubezhnyj-roman` | 1054 | Любовный роман |
| Исторический любовный роман | `/genres/istoricheskij-lyubovnyj-roman` | 948 | Любовный роман |
| Короткий любовный роман | `/genres/korotkij-lyubovnyj-roman` | 3077 | Любовный роман |
| Любовный роман | `/genres/lyubovnyj-roman` | 2268 | Любовный роман |
| Остросюжетный любовный роман | `/genres/ostrosyuzhetnyj-lyubovnyj-roman` | 2927 | Любовный роман |
| Сентиментальный роман | `/genres/sentimentalnyj-roman` | 608 | Любовный роман |
| Современный любовный роман | `/genres/sovremennyj-lyubovnyj-roman` | 7411 | Любовный роман |
| Христианский любовный роман | `/genres/hristianskij-lyubovnyj-roman` | 42 | Любовный роман |
| Эротический роман | `/genres/eroticheskij-roman` | 3348 | Любовный роман |
| Боевик | `/genres/boevik` | 2740 | Остросюжетная литература |
| Детектив | `/genres/detektiv` | 6386 | Остросюжетная литература |
| Детективы, триллеры | `/genres/detektivy-trillery` | 5461 | Остросюжетная литература |
| Зарубежный детектив | `/genres/zarubezhnij-detektiv` | 3244 | Остросюжетная литература |
| Иронический детектив | `/genres/ironicheskij-detektiv` | 728 | Остросюжетная литература |
| Исторический детектив | `/genres/istoricheskij-detektiv` | 1165 | Остросюжетная литература |
| Классический детектив | `/genres/klassicheskij-detektiv` | 1595 | Остросюжетная литература |
| Мистика | `/genres/mistika` | 5104 | Остросюжетная литература |
| Приключения | `/genres/priklyucheniya` | 9414 | Остросюжетная литература |
| Современный детектив | `/genres/sovremennij-detektiv` | 4180 | Остросюжетная литература |
| Триллер | `/genres/triller` | 4358 | Остросюжетная литература |
| Ужасы, мистика | `/genres/uzhasy-mistika` | 9394 | Остросюжетная литература |
| Хоррор/Ужасы | `/genres/horroruzhasy` | 1708 | Остросюжетная литература |
| Young adult | `/genres/young-adult` | 2284 | Проза |
| Драма | `/genres/drama` | 1051 | Проза |
| Зарубежная классика | `/genres/zarubezhnaya-klassika` | 2634 | Проза |
| Историческая проза | `/genres/istoricheskaya-proza` | 2341 | Проза |
| Исторический роман | `/genres/istoricheskij-roman` | 894 | Проза |
| Классика | `/genres/klassika` | 7245 | Проза |
| Магический реализм | `/genres/magicheskij-realizm` | 653 | Проза |
| Повести | `/genres/povesti` | 3443 | Проза |
| Повести и рассказы | `/genres/povesti-i-rasskazy` | 828 | Проза |
| Проза | `/genres/proza` | 1842 | Проза |
| Рассказы | `/genres/rasskazy` | 12475 | Проза |
| Реализм | `/genres/realizm` | 2731 | Проза |
| Романы | `/genres/romany` | 14765 | Проза |
| Советская классика | `/genres/sovetskaya-klassika` | 36 | Проза |
| Современная зарубежная литература | `/genres/sovremennaya-zarubezhnaya-literatura` | 2172 | Проза |
| Современная русская литература | `/genres/sovremennaya-russkaya-literatura` | 2870 | Проза |
| Филгуд | `/genres/feelgood` | 677 | Проза |
| Юмористическая проза | `/genres/yumoristicheskaya-proza` | 1236 | Проза |
| Воспитание детей | `/genres/vospitanie-detey` | 206 | Родителям |
| Детская психология | `/genres/detskaya-psikhologiya` | 168 | Родителям |
| Здоровье детей | `/genres/zdorovye-detey` | 33 | Родителям |
| EVE online | `/genres/eve-online` | 133 | Фантастика |
| S-T-I-K-S | `/genres/s-t-i-k-s` | 191 | Фантастика |
| S.T.A.L.K.E.R. | `/genres/stalker` | 441 | Фантастика |
| Warhammer 40000 | `/genres/warhammer-40000-1` | 376 | Фантастика |
| Альтернативная история | `/genres/alternativnaya-istoriya` | 3305 | Фантастика |
| Боевая фантастика | `/genres/boevaya-fantastika` | 6029 | Фантастика |
| Вселенная метро 2033 | `/genres/vselennaya-metro-2033` | 86 | Фантастика |
| Зарубежная фантастика | `/genres/zarubezhnaya-fantastika` | 1508 | Фантастика |
| Историческая фантастика | `/genres/istoricheskaya-fantastika` | 2342 | Фантастика |
| Классика фантастики | `/genres/klassika-fantastiki` | 619 | Фантастика |
| Космическая фантастика | `/genres/kosmicheskaya-fantastika` | 3367 | Фантастика |
| ЛитРПГ | `/genres/litrpg-1` | 1769 | Фантастика |
| Любовно-фантастический роман | `/genres/lyubovno-fantasticheskij-roman` | 1125 | Фантастика |
| Научная фантастика | `/genres/nauchnaya-fantastika` | 3642 | Фантастика |
| Попаданцы | `/genres/popadantsy` | 12378 | Фантастика |
| Постапокалипсис | `/genres/postapokalipsis` | 2287 | Фантастика |
| Приключенческая фантастика | `/genres/priklyuchencheskaya-fantastika` | 2469 | Фантастика |
| РеалРПГ | `/genres/realrpg` | 1373 | Фантастика |
| Социальная фантастика | `/genres/sotsialnaya-fantastika` | 2511 | Фантастика |
| Технотьма | `/genres/tehnotma` | 10 | Фантастика |
| Фантастика | `/genres/fantastika` | 13945 | Фантастика |
| Фантастика, фэнтези | `/genres/fantastika-fentezi` | 1176 | Фантастика |
| Юмористическая фантастика | `/genres/yumoristicheskaya-fantastika` | 892 | Фантастика |
| Боевое фэнтези | `/genres/boevoe-fentezi` | 6463 | Фэнтези |
| Бояръ-аниме | `/genres/bojar-anime` | 2858 | Фэнтези |
| Бытовое фэнтези | `/genres/bytovoe-fentezi` | 1231 | Фэнтези |
| Героическое фэнтези | `/genres/geroicheskoe-fentezi` | 5983 | Фэнтези |
| Городское фэнтези | `/genres/gorodskoe-fentezi` | 4195 | Фэнтези |
| Детективное фэнтези | `/genres/detektivnoe-fentezi` | 1886 | Фэнтези |
| Дозоры | `/genres/dozory` | 38 | Фэнтези |
| Зарубежное фэнтези | `/genres/zarubezhnoe-fentezi` | 1585 | Фэнтези |
| Историческое фэнтези | `/genres/istoricheskoe-fentezi` | 854 | Фэнтези |
| Любовное фэнтези | `/genres/lyubovnoe-fentezi` | 9796 | Фэнтези |
| Приключенческое фэнтези | `/genres/priklyuchencheskoe-fentezi` | 8318 | Фэнтези |
| Романтическое фэнтези | `/genres/romanticheskoe-fentezi` | 2631 | Фэнтези |
| Русское фэнтези | `/genres/russkoe-fentezi` | 1059 | Фэнтези |
| Фэнтези | `/genres/fentezi` | 2164 | Фэнтези |
| Эротическое фэнтези | `/genres/eroticheskoe-fentezi` | 1272 | Фэнтези |
| Юмористическое фэнтези | `/genres/yumoristicheskoe-fentezi` | 3978 | Фэнтези |
| Аудиоспектакль | `/genres/audiospektakl` | 2999 | Другое |
| Война | `/genres/vojna` | 1580 | Другое |
| На иностранных языках | `/genres/na-inostrannyh-yazykah` | 106 | Другое |
| Подкасты | `/genres/podcast` | 23 | Другое |
| Постмодернизм | `/genres/postmodernizm` | 118 | Другое |
| Поэзия | `/genres/poeziya` | 1175 | Другое |
| Психоделика | `/genres/psihodelika` | 83 | Другое |
| Разное | `/genres/raznoe` | 811 | Другое |
| Ранобэ | `/genres/ranobe` | 1954 | Другое |
| Сюрреализм | `/genres/syurrealizm` | 123 | Другое |
| Тру-крайм | `/genres/tru-krajm` | 777 | Другое |
| Эротическая литература | `/genres/eroticheskaya-literatura` | 82 | Другое |
| Юмор | `/genres/yumor` | 2966 | Другое |

## AUDIOKNIGI_FUN — Аудиокниги.fun (audioknigi.fun)

Source: https://audioknigi.fun/c/zhanr.html (live). One page, flat, ordered by Books desc. Live page equals the offline fixture genres.html.

| Genre (as on site) | Slug / path | Books | Parent |
|---|---|---|---|
| Фантастика | `/zhanr/fantastika/` | 16881 |  |
| Фэнтези | `/zhanr/fjentezi/` | 15392 |  |
| Роман | `/zhanr/roman/` | 9649 |  |
| Проза | `/zhanr/proza/` | 9414 |  |
| Детективы | `/zhanr/detektivy/` | 7646 |  |
| Ужасы | `/zhanr/uzhasy/` | 7222 |  |
| Мистика | `/zhanr/mistika/` | 7152 |  |
| Триллеры | `/zhanr/trillery/` | 7049 |  |
| Для детей | `/zhanr/dlja-detej/` | 3229 |  |
| Попаданцы | `/zhanr/popadancy/` | 2863 |  |
| Аудиосказки | `/zhanr/audioskazki/` | 2753 |  |
| Любовное фэнтези | `/zhanr/ljubovnoe-fjentezi/` | 2299 |  |
| Приключения | `/zhanr/prikljuchenija/` | 2256 |  |
| Классика | `/zhanr/klassika/` | 2225 |  |
| Ранобэ | `/zhanr/ranobje/` | 1898 |  |
| Зарубежная литература | `/zhanr/zarubezhnaja-literatura/` | 1785 |  |
| Сатира | `/zhanr/satira/` | 1648 |  |
| Юмор | `/zhanr/jumor/` | 1648 |  |
| Рассказы | `/zhanr/rasskazy/` | 1580 |  |
| Аудиоспектакли | `/zhanr/audiospektakli/` | 1559 |  |
| Биографии | `/zhanr/biografii/` | 1522 |  |
| Новеллы | `/zhanr/novelly/` | 1418 |  |
| История | `/zhanr/istorija/` | 1305 |  |
| Мемуары | `/zhanr/memuary/` | 1304 |  |
| Психология | `/zhanr/psihologija/` | 1278 |  |
| Философия | `/zhanr/filosofija/` | 1258 |  |
| Боевик | `/zhanr/boevik/` | 1170 |  |
| Разное | `/zhanr/raznoe/` | 930 |  |
| ЛитРПГ | `/zhanr/litrpg/` | 870 |  |
| Эзотерика | `/zhanr/jezoterika/` | 809 |  |
| Познавательная литература | `/zhanr/poznavatelnaja-literatura/` | 702 |  |
| Религия | `/zhanr/religija/` | 647 |  |
| Альтернативная история | `/zhanr/alternativnaja-istorija/` | 617 |  |
| Научно-популярное | `/zhanr/nauchno-populjarnoe/` | 573 |  |
| Любовный Роман | `/zhanr/ljubovnyj-roman/` | 558 |  |
| Постапокалипсис | `/zhanr/postapokalipsis/` | 552 |  |
| Поэзия | `/zhanr/pojezija/` | 474 |  |
| Русская литература | `/zhanr/russkaja-literatura/` | 393 |  |
| Бизнес | `/zhanr/biznes/` | 380 |  |
| Stalker | `/zhanr/stalker/` | 291 |  |
| Для взрослых | `/zhanr/dlja-vzroslyh/` | 217 |  |
| Здоровье | `/zhanr/zdorove/` | 209 |  |
| Медицина | `/zhanr/medicina/` | 209 |  |
| Современная проза | `/zhanr/sovremennaja-proza/` | 204 |  |
| Криминал | `/zhanr/kriminal/` | 185 |  |
| Warhammer 40000 | `/zhanr/warhammer-40000/` | 137 |  |
| Обучение | `/zhanr/obuchenie/` | 135 |  |
| Военный | `/zhanr/voennyj/` | 128 |  |
| S-T-I-K-S | `/zhanr/s-t-i-k-s/` | 119 |  |
| Саморазвитие | `/zhanr/samorazvitie/` | 93 |  |
| На иностранных языках | `/zhanr/na-inostrannyh-jazykah/` | 91 |  |
| Киберпанк | `/zhanr/kiberpank/` | 79 |  |
| Тру-крайм | `/zhanr/tru-krajm/` | 72 |  |
| Метро 2033 | `/zhanr/metro-2033/` | 60 |  |
| Бояръ-Аниме | `/zhanr/bojar-anime/` | 32 |  |
| Eve Online | `/zhanr/eve-online/` | 29 |  |
| Этногенез | `/zhanr/jetnogenez/` | 21 |  |

## AKNIG_XYZ — Aknig.xyz (s2.aknig.xyz)

Source: https://s2.aknig.xyz/genres.html (live). No counts. The page also lists a hidden service category "ban" (/zablokirovannye-knigi/, taken-down books): left out as the adapter does. Live page equals the offline fixture genres.html.

| Genre (as on site) | Slug / path | Books | Parent |
|---|---|---|---|
| Warhammer 40000 | `/warhammer-40000/` |  |  |
| Аудиоспектакль | `/audiospektakl/` |  |  |
| LitRPG | `/litrpg/` |  |  |
| Альтернативная история | `/alternativnaja-istorija/` |  |  |
| Бизнес | `/biznes/` |  |  |
| Биографии, мемуары | `/biografii-memuary/` |  |  |
| Боевики | `/boeviki/` |  |  |
| Детективы, триллеры | `/detektivy-trillery/` |  |  |
| Для детей | `/dlja-detej/` |  |  |
| История | `/istorija/` |  |  |
| Классика | `/klassika/` |  |  |
| Любовное фэнтези | `/ljubovnoe-fjentezi/` |  |  |
| Любовный роман | `/ljubovnyj-roman/` |  |  |
| Медицина, здоровье | `/medicina-zdorove/` |  |  |
| Научно-популярное | `/nauchno-populjarnoe/` |  |  |
| Обучение | `/obuchenie/` |  |  |
| Познавательная литература | `/poznavatelnaja-literatura/` |  |  |
| Попаданцы | `/popadancy/` |  |  |
| Постапокалипсис | `/postapokalipsis/` |  |  |
| Поэзия | `/pojezija/` |  |  |
| Приключения | `/prikljuchenija/` |  |  |
| Психология, философия | `/psihologija-filosofija/` |  |  |
| Разное | `/raznoe/` |  |  |
| Ранобэ | `/ranobje/` |  |  |
| Религия | `/religija/` |  |  |
| Роман, проза | `/roman-proza/` |  |  |
| Сказки | `/skazki/` |  |  |
| Ужасы, мистика | `/uzhasy-mistika/` |  |  |
| Фантастика, фэнтези | `/fantastika-fjentezi/` |  |  |
| Эзотерика | `/jezoterika/` |  |  |
| Юмор, сатира | `/jumor-satira/` |  |  |
| EVE online | `/eve-online/` |  |  |
| S.T.A.L.K.E.R. | `/stalker/` |  |  |
| S-T-I-K-S | `/s-t-i-k-s/` |  |  |
| Метро 2033 | `/metro-2033/` |  |  |

## AUDIOKNIGA_LIFE — Аудиокнига Life (audiokniga.life)

Source: https://audiokniga.life/ (live). Genre list = the sidebar block of the home page. Subgenres are nested in the URL (/fantastika/postapokalipsis/): Parent was derived from the first path segment. Books = count shown (a subgenre's count is not part of its parent's).

| Genre (as on site) | Slug / path | Books | Parent |
|---|---|---|---|
| Фантастика | `/fantastika/` | 1507 |  |
| Попаданцы | `/popadancy/` | 840 |  |
| Постапокалипсис | `/fantastika/postapokalipsis/` | 269 | Фантастика |
| Фэнтези | `/fjentezi/` | 837 |  |
| Любовное фэнтези | `/fjentezi/ljubovnoe-fjentezi/` | 237 | Фэнтези |
| ЛитРПГ | `/litrpg/` | 222 |  |
| Детективы | `/detektivy/` | 609 |  |
| Русские детективы | `/russkie-detektivy/` | 370 |  |
| Ужасы | `/uzhasy/` | 368 |  |
| Мистика | `/mistika/` | 314 |  |
| Приключение | `/prikljuchenija/` | 297 |  |
| Роман | `/roman/` | 739 |  |
| Классика | `/klassika/` | 432 |  |
| Психология | `/psihologija/` | 103 |  |
| Биография | `/biografija/` | 89 |  |
| История | `/istorija/` | 94 |  |
| Аудиосказки | `/audioskazki/` | 46 |  |
| S.T.A.L.K.E.R. | `/fantastika/stalker/` | 70 | Фантастика |
| Про зомби | `/pro-zombi/` | 29 |  |

## Cross-source index

One row per distinct genre name. Normalized key = lowercase, trimmed, `ё`->`е`, punctuation removed (`. ' " « » ( ) ! ?` and hyphens/dashes deleted, `,` `/` `;` `:` turned into a space, spaces collapsed). Variants = the spellings found, separated by ` | `. Sites = Provider enums in registry order. Sub-entries (Akniga labels, Izibuk subcategories, Audiokniga.life and Uknig children) count as names like any other; group headings (Uknig) are not included. Sorted by Count desc, then key.

| Normalized key | Variants as written | Sites (enum names) | Count of sites |
|---|---|---|---|
| классика | Классика | AKNIGA, KNIGAVUHE, IZIBUK, ABMP3, BAZA_KNIG, KNIGOBLUD, AUDIOBOO, OTRUB, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ, AUDIOKNIGA_LIFE | 13 |
| поэзия | Поэзия | AKNIGA, KNIGAVUHE, IZIBUK, ABMP3, BAZA_KNIG, KNIGOBLUD, AUDIOBOO, OTRUB, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 12 |
| религия | Религия | AKNIGA, KNIGAVUHE, IZIBUK, ABMP3, BAZA_KNIG, KNIGOBLUD, AUDIOBOO, OTRUB, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 12 |
| научнопопулярное | Научно-популярное | AKNIGA, KNIGAVUHE, IZIBUK, ABMP3, BAZA_KNIG, KNIGOBLUD, OTRUB, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 11 |
| обучение | Обучение | AKNIGA, KNIGAVUHE, IZIBUK, ABMP3, BAZA_KNIG, KNIGOBLUD, OTRUB, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 11 |
| разное | Разное | AKNIGA, KNIGAVUHE, IZIBUK, ABMP3, BAZA_KNIG, KNIGOBLUD, OTRUB, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 11 |
| история | История | AKNIGA, KNIGAVUHE, ABMP3, BAZA_KNIG, AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ, AUDIOKNIGA_LIFE | 10 |
| бизнес | Бизнес | AKNIGA, KNIGAVUHE, ABMP3, BAZA_KNIG, AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 9 |
| медицина здоровье | Медицина, здоровье | AKNIGA, IZIBUK, ABMP3, BAZA_KNIG, KNIGOBLUD, OTRUB, POLEKNIG, UKNIG, AKNIG_XYZ | 9 |
| на иностранных языках | На иностранных языках | AKNIGA, KNIGAVUHE, IZIBUK, ABMP3, KNIGOBLUD, OTRUB, POLEKNIG, UKNIG, AUDIOKNIGI_FUN | 9 |
| психология философия | Психология, философия | AKNIGA, IZIBUK, ABMP3, BAZA_KNIG, KNIGOBLUD, OTRUB, POLEKNIG, UKNIG, AKNIG_XYZ | 9 |
| ранобэ | Ранобэ | IZIBUK, ABMP3, BAZA_KNIG, KNIGOBLUD, AUDIOBOO, OTRUB, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 9 |
| фантастика фэнтези | Фантастика, фэнтези | AKNIGA, IZIBUK, ABMP3, BAZA_KNIG, KNIGOBLUD, OTRUB, POLEKNIG, UKNIG, AKNIG_XYZ | 9 |
| эзотерика | Эзотерика | AKNIGA, KNIGAVUHE, ABMP3, BAZA_KNIG, AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 9 |
| stalker | S.T.A.L.K.E.R. \| Stalker | IZIBUK, BAZA_KNIG, AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ, AUDIOKNIGA_LIFE | 8 |
| попаданцы | Попаданцы | IZIBUK, BAZA_KNIG, AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ, AUDIOKNIGA_LIFE | 8 |
| приключения | Приключения | AKNIGA, ABMP3, BAZA_KNIG, AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 8 |
| любовное фэнтези | Любовное Фэнтези \| Любовное фэнтези | IZIBUK, BAZA_KNIG, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ, AUDIOKNIGA_LIFE | 7 |
| постапокалипсис | Постапокалипсис | BAZA_KNIG, AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ, AUDIOKNIGA_LIFE | 7 |
| роман проза | Роман, проза | AKNIGA, IZIBUK, ABMP3, BAZA_KNIG, KNIGOBLUD, OTRUB, AKNIG_XYZ | 7 |
| юмор сатира | Юмор, сатира | AKNIGA, IZIBUK, ABMP3, BAZA_KNIG, KNIGOBLUD, OTRUB, AKNIG_XYZ | 7 |
| stiks | S-T-I-K-S | IZIBUK, BAZA_KNIG, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 6 |
| альтернативная история | Альтернативная история | BAZA_KNIG, AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 6 |
| биографии мемуары | Биографии, мемуары | AKNIGA, ABMP3, BAZA_KNIG, POLEKNIG, UKNIG, AKNIG_XYZ | 6 |
| детективы триллеры | Детективы, триллеры | AKNIGA, ABMP3, BAZA_KNIG, POLEKNIG, UKNIG, AKNIG_XYZ | 6 |
| литрпг | ЛитРПГ | AKNIGA, IZIBUK, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AUDIOKNIGA_LIFE | 6 |
| познавательная литература | Познавательная литература | BAZA_KNIG, AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 6 |
| трукрайм | Тру-крайм | KNIGAVUHE, IZIBUK, ABMP3, POLEKNIG, UKNIG, AUDIOKNIGI_FUN | 6 |
| ужасы мистика | Ужасы, мистика | AKNIGA, ABMP3, BAZA_KNIG, POLEKNIG, UKNIG, AKNIG_XYZ | 6 |
| фантастика | Фантастика | AKNIGA, AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AUDIOKNIGA_LIFE | 6 |
| фэнтези | Фэнтези | AKNIGA, AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AUDIOKNIGA_LIFE | 6 |
| этногенез | Этногенез | IZIBUK, BAZA_KNIG, AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN | 6 |
| eve online | EVE online \| Eve Online | BAZA_KNIG, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 5 |
| warhammer 40000 | Warhammer 40000 | AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 5 |
| аудиоспектакль | Аудиоспектакль | BAZA_KNIG, AUDIOBOO, POLEKNIG, UKNIG, AKNIG_XYZ | 5 |
| боевик | Боевик | AKNIGA, AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN | 5 |
| для детей | Для детей | ABMP3, BAZA_KNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 5 |
| любовный роман | Любовный Роман \| Любовный роман | BAZA_KNIG, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 5 |
| мистика | Мистика | AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AUDIOKNIGA_LIFE | 5 |
| психология | Психология | AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN, AUDIOKNIGA_LIFE | 5 |
| сказки | Сказки | AKNIGA, BAZA_KNIG, POLEKNIG, UKNIG, AKNIG_XYZ | 5 |
| аудиоспектакли | Аудиоспектакли | AKNIGA, KNIGAVUHE, ABMP3, AUDIOKNIGI_FUN | 4 |
| детектив | Детектив | AKNIGA, AUDIOBOO, POLEKNIG, UKNIG | 4 |
| метро 2033 | Метро 2033 | IZIBUK, BAZA_KNIG, AUDIOKNIGI_FUN, AKNIG_XYZ | 4 |
| приключения военные приключения | Приключения, военные приключения | IZIBUK, KNIGOBLUD, OTRUB, POLEKNIG | 4 |
| проза | Проза | AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN | 4 |
| триллер | Триллер | AKNIGA, AUDIOBOO, POLEKNIG, UKNIG | 4 |
| философия | Философия | AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN | 4 |
| юмор | Юмор | AUDIOBOO, POLEKNIG, UKNIG, AUDIOKNIGI_FUN | 4 |
| litrpg | LitRPG | BAZA_KNIG, AUDIOBOO, AKNIG_XYZ | 3 |
| аудиоспектакли радиопостановки и литературные чтения | Аудиоспектакли, радиопостановки и литературные чтения | IZIBUK, KNIGOBLUD, OTRUB | 3 |
| бизнес личностный рост | Бизнес, личностный рост | IZIBUK, KNIGOBLUD, OTRUB | 3 |
| биографии мемуары жзл | Биографии, мемуары, ЖЗЛ | IZIBUK, KNIGOBLUD, OTRUB | 3 |
| бояръаниме | Бояръ-Аниме \| Бояръ-аниме | POLEKNIG, UKNIG, AUDIOKNIGI_FUN | 3 |
| война | Война | AUDIOBOO, POLEKNIG, UKNIG | 3 |
| вселенная метро 2033 | Вселенная метро 2033 | AUDIOBOO, POLEKNIG, UKNIG | 3 |
| детективы триллеры боевики | Детективы, триллеры, боевики | IZIBUK, KNIGOBLUD, OTRUB | 3 |
| для детей аудиосказки стишки | Для детей, аудиосказки, стишки | IZIBUK, KNIGOBLUD, OTRUB | 3 |
| драма | Драма | AUDIOBOO, POLEKNIG, UKNIG | 3 |
| история культурология | История, культурология | IZIBUK, KNIGOBLUD, OTRUB | 3 |
| магический реализм | Магический реализм | AKNIGA, POLEKNIG, UKNIG | 3 |
| рассказы | Рассказы | POLEKNIG, UKNIG, AUDIOKNIGI_FUN | 3 |
| роман | Роман | AUDIOBOO, AUDIOKNIGI_FUN, AUDIOKNIGA_LIFE | 3 |
| технотьма | Технотьма | BAZA_KNIG, POLEKNIG, UKNIG | 3 |
| ужасы | Ужасы | AUDIOBOO, AUDIOKNIGI_FUN, AUDIOKNIGA_LIFE | 3 |
| ужасы мистика хоррор | Ужасы, мистика, хоррор | IZIBUK, KNIGOBLUD, OTRUB | 3 |
| учебник | Учебник | AUDIOBOO, POLEKNIG, UKNIG | 3 |
| эзотерика нетрадиционные религиознофилософские учения | Эзотерика, Нетрадиционные религиозно-философские учения | IZIBUK, KNIGOBLUD, OTRUB | 3 |
| аудиосказки | Аудиосказки | AUDIOKNIGI_FUN, AUDIOKNIGA_LIFE | 2 |
| биография | Биография | AUDIOBOO, AUDIOKNIGA_LIFE | 2 |
| боевая фантастика | Боевая фантастика | POLEKNIG, UKNIG | 2 |
| боевики | Боевики | BAZA_KNIG, AKNIG_XYZ | 2 |
| боевое фэнтези | Боевое фэнтези | POLEKNIG, UKNIG | 2 |
| бытовое фэнтези | Бытовое фэнтези | POLEKNIG, UKNIG | 2 |
| героическое фэнтези | Героическое фэнтези | POLEKNIG, UKNIG | 2 |
| городское фэнтези | Городское фэнтези | POLEKNIG, UKNIG | 2 |
| детективное фэнтези | Детективное фэнтези | POLEKNIG, UKNIG | 2 |
| детективы | Детективы | AUDIOKNIGI_FUN, AUDIOKNIGA_LIFE | 2 |
| дозоры | Дозоры | POLEKNIG, UKNIG | 2 |
| историческая проза | Историческая проза | POLEKNIG, UKNIG | 2 |
| историческая фантастика | Историческая фантастика | POLEKNIG, UKNIG | 2 |
| исторический любовный роман | Исторический любовный роман | POLEKNIG, UKNIG | 2 |
| медицина | Медицина | AUDIOBOO, AUDIOKNIGI_FUN | 2 |
| мемуары | Мемуары | AUDIOBOO, AUDIOKNIGI_FUN | 2 |
| повести | Повести | POLEKNIG, UKNIG | 2 |
| повести и рассказы | Повести и рассказы | POLEKNIG, UKNIG | 2 |
| постмодернизм | Постмодернизм | POLEKNIG, UKNIG | 2 |
| приключения вестерны и боевики | Приключения, вестерны и боевики | KNIGAVUHE, POLEKNIG | 2 |
| приключенческое фэнтези | Приключенческое фэнтези | POLEKNIG, UKNIG | 2 |
| психоделика | Психоделика | POLEKNIG, UKNIG | 2 |
| публицистика | Публицистика | AUDIOBOO, UKNIG | 2 |
| ранобэ новеллы | Ранобэ, новеллы | AKNIGA, POLEKNIG | 2 |
| реализм | Реализм | POLEKNIG, UKNIG | 2 |
| роман и проза | Роман и проза | KNIGAVUHE, POLEKNIG | 2 |
| романтическое фэнтези | Романтическое фэнтези | POLEKNIG, UKNIG | 2 |
| романы | Романы | POLEKNIG, UKNIG | 2 |
| саморазвитие | Саморазвитие | UKNIG, AUDIOKNIGI_FUN | 2 |
| стихи | Стихи | AUDIOBOO, POLEKNIG | 2 |
| сюрреализм | Сюрреализм | POLEKNIG, UKNIG | 2 |
| хоррор ужасы | Хоррор/Ужасы | POLEKNIG, UKNIG | 2 |
| эротика | Эротика | AUDIOBOO, POLEKNIG | 2 |
| эротическое фэнтези | Эротическое фэнтези | POLEKNIG, UKNIG | 2 |
| young adult | Young adult | UKNIG | 1 |
| античность | Античность | AUDIOBOO | 1 |
| аудиосказки и рассказы для детей | Аудиосказки и рассказы для детей | AKNIGA | 1 |
| биографии | Биографии | AUDIOKNIGI_FUN | 1 |
| биографии и мемуары | Биографии и мемуары | KNIGAVUHE | 1 |
| военный | Военный | AUDIOKNIGI_FUN | 1 |
| воспитание детей | Воспитание детей | UKNIG | 1 |
| высокие технологии | Высокие технологии | UKNIG | 1 |
| детективы и триллеры | Детективы и триллеры | KNIGAVUHE | 1 |
| детская литература | Детская литература | AUDIOBOO | 1 |
| детская психология | Детская психология | UKNIG | 1 |
| для взрослых | Для взрослых | AUDIOKNIGI_FUN | 1 |
| для детей аудиосказки | Для детей, Аудиосказки | POLEKNIG | 1 |
| для детей и аудиосказки | Для детей и Аудиосказки | KNIGAVUHE | 1 |
| документальная литература | Документальная литература | UKNIG | 1 |
| зарубежная классика | Зарубежная классика | UKNIG | 1 |
| зарубежная литература | Зарубежная литература | AUDIOKNIGI_FUN | 1 |
| зарубежная фантастика | Зарубежная фантастика | UKNIG | 1 |
| зарубежное фэнтези | Зарубежное фэнтези | UKNIG | 1 |
| зарубежный детектив | Зарубежный детектив | UKNIG | 1 |
| зарубежный любовный роман | Зарубежный любовный роман | UKNIG | 1 |
| здоровье | Здоровье | AUDIOKNIGI_FUN | 1 |
| здоровье детей | Здоровье детей | UKNIG | 1 |
| интервью | Интервью | AUDIOBOO | 1 |
| иронический детектив | Иронический детектив | UKNIG | 1 |
| исторический детектив | Исторический детектив | UKNIG | 1 |
| исторический роман | Исторический роман | UKNIG | 1 |
| историческое фэнтези | Историческое фэнтези | UKNIG | 1 |
| киберпанк | Киберпанк | AUDIOKNIGI_FUN | 1 |
| классика фантастики | Классика фантастики | UKNIG | 1 |
| классический детектив | Классический детектив | UKNIG | 1 |
| короткий любовный роман | Короткий любовный роман | UKNIG | 1 |
| космическая фантастика | Космическая фантастика | UKNIG | 1 |
| криминал | Криминал | AUDIOKNIGI_FUN | 1 |
| лекция | Лекция | AUDIOBOO | 1 |
| личные финансы | Личные финансы | UKNIG | 1 |
| лффр | ЛФФР | AUDIOBOO | 1 |
| любовнофантастический роман | Любовно-фантастический роман | UKNIG | 1 |
| медицина и здоровье | Медицина и здоровье | KNIGAVUHE | 1 |
| научная фантастика | Научная фантастика | UKNIG | 1 |
| новелла | Новелла | AUDIOBOO | 1 |
| новеллы | Новеллы | AUDIOKNIGI_FUN | 1 |
| ораторское искусство риторика | Ораторское искусство, риторика | UKNIG | 1 |
| остросюжетный любовный роман | Остросюжетный любовный роман | UKNIG | 1 |
| повесть | Повесть | AUDIOBOO | 1 |
| подкасты | Подкасты | UKNIG | 1 |
| приключение | Приключение | AUDIOKNIGA_LIFE | 1 |
| приключения и вестерн | Приключения и вестерн | POLEKNIG | 1 |
| приключенческая фантастика | Приключенческая фантастика | UKNIG | 1 |
| притча | Притча | AUDIOBOO | 1 |
| про зомби | Про зомби | AUDIOKNIGA_LIFE | 1 |
| психология и философия | Психология и философия | KNIGAVUHE | 1 |
| путешествия | Путешествия | AKNIGA | 1 |
| радиоспектакли | Радиоспектакли | POLEKNIG | 1 |
| ранобэ и новеллы | Ранобэ и новеллы | KNIGAVUHE | 1 |
| реалрпг | РеалРПГ | UKNIG | 1 |
| русская литература | Русская литература | AUDIOKNIGI_FUN | 1 |
| русские детективы | Русские детективы | AUDIOKNIGA_LIFE | 1 |
| русское фэнтези | Русское фэнтези | UKNIG | 1 |
| сатира | Сатира | AUDIOKNIGI_FUN | 1 |
| семейная психология | Семейная психология | UKNIG | 1 |
| сентиментальный роман | Сентиментальный роман | UKNIG | 1 |
| сказка | Сказка | AUDIOBOO | 1 |
| советская классика | Советская классика | UKNIG | 1 |
| современная зарубежная литература | Современная зарубежная литература | UKNIG | 1 |
| современная проза | Современная проза | AUDIOKNIGI_FUN | 1 |
| современная русская литература | Современная русская литература | UKNIG | 1 |
| современный детектив | Современный детектив | UKNIG | 1 |
| современный любовный роман | Современный любовный роман | UKNIG | 1 |
| социальная психология | Социальная психология | UKNIG | 1 |
| социальная фантастика | Социальная фантастика | UKNIG | 1 |
| триллеры | Триллеры | AUDIOKNIGI_FUN | 1 |
| трєш | Трєш | AUDIOBOO | 1 |
| ужасы и мистика | Ужасы и мистика | KNIGAVUHE | 1 |
| фантастика и фэнтези | Фантастика и фэнтези | KNIGAVUHE | 1 |
| филгуд | Филгуд | UKNIG | 1 |
| хоррор | Хоррор | AUDIOBOO | 1 |
| христианский любовный роман | Христианский любовный роман | UKNIG | 1 |
| экономика | Экономика | UKNIG | 1 |
| эротическая литература | Эротическая литература | UKNIG | 1 |
| эротический роман | Эротический роман | UKNIG | 1 |
| юмор и сатира | Юмор и сатира | KNIGAVUHE | 1 |
| юмористическая проза | Юмористическая проза | UKNIG | 1 |
| юмористическая фантастика | Юмористическая фантастика | UKNIG | 1 |
| юмористическое фэнтези | Юмористическое фэнтези | UKNIG | 1 |
