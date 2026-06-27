import 'package:flutter_test/flutter_test.dart';
import 'package:islamic_ai_mobile/l10n/strings.dart';

void main() {
  test('supports four language keys', () {
    expect(Strings.supported, containsAll(['en', 'ru', 'uz', 'uzk']));
    expect(Strings('en').t('login'), 'Login');
    expect(Strings('uz').t('send'), isNotEmpty);
  });
}
