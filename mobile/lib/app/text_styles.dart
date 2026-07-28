import 'package:flutter/material.dart';

abstract class AppTextStyles {
  // The previous bundled Nunito/Playfair files were HTML error pages rather
  // than fonts. Use the platform typography until valid font assets are added.
  static const String? _head = null;
  static const String? _body = null;
  static const _arabic = 'Amiri';

  // SARLAVHALAR (Playfair Display)
  static const displayLarge = TextStyle(
    fontFamily: _head,
    fontSize: 32,
    fontWeight: FontWeight.w700,
    height: 40 / 32,
    letterSpacing: -0.5,
  );

  static const displayMedium = TextStyle(
    fontFamily: _head,
    fontSize: 28,
    fontWeight: FontWeight.w700,
    height: 36 / 28,
  );

  static const headlineLarge = TextStyle(
    fontFamily: _head,
    fontSize: 24,
    fontWeight: FontWeight.w600,
    height: 32 / 24,
  );

  static const headlineMedium = TextStyle(
    fontFamily: _head,
    fontSize: 20,
    fontWeight: FontWeight.w600,
    height: 28 / 20,
  );

  static const titleLarge = TextStyle(
    fontFamily: _head,
    fontSize: 18,
    fontWeight: FontWeight.w600,
    height: 24 / 18,
  );

  // ASOSIY MATN (Nunito)
  static const bodyLarge = TextStyle(
    fontFamily: _body,
    fontSize: 16,
    fontWeight: FontWeight.w400,
    height: 24 / 16,
  );

  static const bodyMedium = TextStyle(
    fontFamily: _body,
    fontSize: 14,
    fontWeight: FontWeight.w400,
    height: 20 / 14,
  );

  static const labelLarge = TextStyle(
    fontFamily: _body,
    fontSize: 14,
    fontWeight: FontWeight.w600,
    height: 20 / 14,
  );

  static const labelMedium = TextStyle(
    fontFamily: _body,
    fontSize: 12,
    fontWeight: FontWeight.w600,
    height: 16 / 12,
  );

  static const labelSmall = TextStyle(
    fontFamily: _body,
    fontSize: 11,
    fontWeight: FontWeight.w300,
    height: 16 / 11,
  );

  // ARABCHA MATN (Amiri)
  static const arabicLarge = TextStyle(
    fontFamily: _arabic,
    fontSize: 26,
    fontWeight: FontWeight.w400,
    height: 44 / 26,
  );

  static const arabicMedium = TextStyle(
    fontFamily: _arabic,
    fontSize: 20,
    fontWeight: FontWeight.w400,
    height: 36 / 20,
  );

  static const arabicSmall = TextStyle(
    fontFamily: _arabic,
    fontSize: 16,
    fontWeight: FontWeight.w400,
    height: 30 / 16,
  );
}
