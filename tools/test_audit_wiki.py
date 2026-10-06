"""Offline regressions for the independent Wiki-page audit reader."""
from fractions import Fraction
import unittest
from audit_wiki import DropHTML, arithmetic, fields, probability, quantity, templates, page_evidence, same_rate


class WikiAuditReaderTest(unittest.TestCase):
    def test_bucket_item_anchor_and_quest_notes_are_preserved(self):
        text = '{{DropsTableHead|dropversion=Level 24}}{{DropsLine|name=Zombie bone|bucketname=Zombie bone#Unpolished|quantity=1|rarity=1/4|raritynotes=Only during Rag and Bone Man II}}'
        data = {"parse": {"wikitext": {"*": text}, "revid": 1}, "fetchedAt": "2026-10-06"}
        row = page_evidence("Zombie", data)["rows"][0]
        self.assertEqual("Zombie bone#Unpolished", row["item"])
        self.assertIn("Only during", row["notes"])

    def test_decimal_denominator_and_fixed_roll_prefix(self):
        self.assertEqual(Fraction(5, 1079), probability("1/215.8"))
        self.assertEqual(Fraction(1, 1024), probability("2 × 1/1,024[1]"))
        self.assertEqual(Fraction(1, 4), probability("25%"))
        self.assertIsNone(probability("Rare"))
        self.assertIsNone(probability("1/0"))
        self.assertTrue(same_rate("Common[1]", "Common"))
        self.assertFalse(same_rate("Rare", "Unknown"))
        self.assertTrue(same_rate("2 × 1/128", "1/128"))
        self.assertTrue(same_rate("3 × ~1/64", "1/64"))
        self.assertTrue(same_rate("11 × Varies", "Varies"))

    def test_quantities_compare_without_visual_footnotes(self):
        self.assertEqual(quantity("1,000-2,000[2]"), quantity("1000–2000"))
        self.assertEqual(quantity("12, 15"), quantity("12; 15"))
        self.assertEqual(quantity("5\u00e2\u20ac\u201c10\u00c2\u00a0(noted)"), quantity("5–10 (noted)"))

    def test_explicit_row_variant_and_wilderness_condition(self):
        text = '{{DropsTableHead}}{{DropsLine|name=Looting bag|dropversion=Level 19|quantity=1|rarity=1/7|raritynotes=Looting bags are only dropped by those found in the [[Wilderness]].}}'
        row = page_evidence("Ghost", {"parse": {"wikitext": {"*": text}, "revid": 1}, "fetchedAt": "2026-10-06"})["rows"][0]
        self.assertEqual("Ghost#Level 19", row["table"])
        self.assertEqual("Wilderness", row["requiredArea"])

    def test_nested_templates_and_link_pipes_remain_intact(self):
        text = "{{DropsLine|name=[[Coins|coins]]|rarity={{#expr:1 / 128}}|quantity=2}}"
        parsed = [fields(body) for _, body in templates(text)]
        self.assertEqual("[[Coins|coins]]", parsed[0][1]["name"])
        self.assertEqual("{{#expr:1 / 128}}", parsed[0][1]["rarity"])
        self.assertEqual(Fraction(1, 128), probability(parsed[0][1]["rarity"]))

    def test_arithmetic_does_not_execute_wiki_calls(self):
        self.assertEqual("4", arithmetic("(2 + 6) / 2"))
        self.assertEqual("unresolved", arithmetic("__import__('os').getcwd()"))
        self.assertEqual("unresolved", arithmetic("2 ** 1000000000"))

    def test_rendered_rows_keep_repeat_outcomes_and_canonical_page_title(self):
        row = '<tr><td class="item-col"><img alt="File: Chicken drops Feather with rarity 1/4"><a href="/w/Feather" title="Feather">Feathers</a></td><td>5–15</td><td><span data-drop-fraction="1/4">Uncommon</span></td></tr>'
        parser = DropHTML("Chicken")
        parser.feed('<table class="item-drops">' + row + row + '</table>')
        self.assertEqual(2, len(parser.rows))
        self.assertEqual("Feather", parser.rows[0]["item"])
        self.assertEqual("1/4", parser.rows[0]["rate"])


if __name__ == "__main__":
    unittest.main()
