import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

abstract class AppTextStyles {
  static TextStyle get _head => GoogleFonts.outfit();
  static TextStyle get _body => GoogleFonts.inter();
  static const _arabic = 'Amiri';

  static TextStyle get displayLarge => _head.copyWith(
        fontSize: 32,
        fontWeight: FontWeight.w700,
        height: 40 / 32,
        letterSpacing: -0.5,
      );

  static TextStyle get displayMedium => _head.copyWith(
        fontSize: 28,
        fontWeight: FontWeight.w700,
        height: 36 / 28,
      );

  static TextStyle get headlineLarge => _head.copyWith(
        fontSize: 24,
        fontWeight: FontWeight.w600,
        height: 32 / 24,
      );

  static TextStyle get headlineMedium => _head.copyWith(
        fontSize: 20,
        fontWeight: FontWeight.w600,
        height: 28 / 20,
      );

  static TextStyle get titleLarge => _head.copyWith(
        fontSize: 18,
        fontWeight: FontWeight.w600,
        height: 24 / 18,
      );

  static TextStyle get bodyLarge => _body.copyWith(
        fontSize: 16,
        fontWeight: FontWeight.w400,
        height: 24 / 16,
      );

  static TextStyle get bodyMedium => _body.copyWith(
        fontSize: 14,
        fontWeight: FontWeight.w400,
        height: 20 / 14,
      );

  static TextStyle get labelLarge => _body.copyWith(
        fontSize: 14,
        fontWeight: FontWeight.w600,
        height: 20 / 14,
      );

  static TextStyle get labelMedium => _body.copyWith(
        fontSize: 12,
        fontWeight: FontWeight.w600,
        height: 16 / 12,
      );

  static TextStyle get labelSmall => _body.copyWith(
        fontSize: 11,
        fontWeight: FontWeight.w400,
        height: 16 / 11,
      );

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
