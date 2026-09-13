// Pure JScript helpers plus source contract; actual MSHTML verification is separate.
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const root = path.resolve(__dirname, '../..');
const read = name => fs.readFileSync(path.join(root, name), 'utf8');
const view = read('admin_data_menu/views/clan-economy.html');
assert(!view.includes('clanEconomyReportTab'), 'Economic report tab must be removed');
const context = vm.createContext({ RegisterTab: value => { context.tab = value; } });
for (const file of ['js/core/ui.js', 'js/core/utils.js', 'js/components/config.js', 'js/tabs/clan-economy.js']) {
  vm.runInContext(read('admin_data_menu/' + file), context);
}
const row = (kind, min, max, count) => ['test', 'Nhóm khác', 'Tên cấu hình', '', '', kind, '', '', 'test_property', 'Mô tả', min || '', max || '', String(count || 0)];
assert.equal(context.tab.title, 'Cấu hình bang');
assert.equal(context.ClanConfigValidateValue(row('long', '0', '9223372036854775807'), '9223372036854775807'), '');
assert(context.ClanConfigValidateValue(row('long', '0', '9223372036854775807'), '9223372036854775808'));
assert(context.ClanConfigValidateValue(row('percent', '0', '100'), '101'));
assert(context.ClanConfigValidateValue(row('int', '0', '100'), '-1'));
assert(context.ClanConfigValidateValue(row('int', '0', '100'), '1.5'), 'No silent decimal-to-integer coercion');
assert.equal(context.ClanConfigNormalizeValue('1.000.000', 'long'), '1000000');
assert.equal(context.ClanConfigNormalizeValue('1.5', 'int'), '1.5');
assert.equal(context.ClanConfigValidateValue(row('decimal', '0.10', '10'), '1.4'), '');
assert(context.ClanConfigValidateValue(row('decimal', '0.10', '10'), '1,4'));
assert.equal(context.ClanConfigValidateValue(row('bool'), 'false'), '');
assert(context.ClanConfigValidateValue(row('bool'), 'yes'));
assert.equal(context.ClanConfigValidateValue(row('hex-color'), '0xFFE082'), '');
assert(context.ClanConfigValidateValue(row('hex-color'), 'red;display:none'));
assert.equal(context.ClanConfigValidateValue(row('resource-prefix'), 'cay_lv_v4_'), '');
assert(context.ClanConfigValidateValue(row('resource-prefix'), '../tree_'));
assert(context.ClanConfigValidateValue(row('java-text'), 'abc\nkey=true'));
assert(context.ClanConfigValidateValue(row('int-list', '0', '3650', 19), '1,2'));
assert.equal(context.ClanConfigValidateValue(row('int-list', '0', '3650', 19), Array(19).fill('2').join(',')), '');
assert.equal(context.ClanConfigFieldLabels(row('int-list', '0', '3650', 19)).length, 19);
assert.equal(context.ClanConfigFieldLabels(row('shop-item', '', '', 7)).length, 7);
assert.equal(context.ClanConfigValidateValue(row('shop-item', '', '', 7), '3,12,0,9223372036854775807,4,0,1'), '');
assert(context.ClanConfigValidateValue(row('shop-item', '', '', 7), '4,12,0,0,4,0,1'));
assert(context.ClanConfigSearchMatches(row('int'), 'nhom khac'), 'Search ignores Vietnamese accents');
console.log('ClanConfigUiTest: PASS');
