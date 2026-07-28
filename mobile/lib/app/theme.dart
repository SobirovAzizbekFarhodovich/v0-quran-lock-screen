import 'package:flutter/material.dart';

class AppTheme {
  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      colorScheme: const ColorScheme.light(
        primary:          Color(0xFF1B4D3E),
        onPrimary:        Color(0xFFFFFFFF),
        primaryContainer: Color(0xFF9FD4B2),
        secondary:        Color(0xFFC9A96E),
        onSecondary:      Color(0xFF1A2B22),
        surface:          Color(0xFFFFFFFF),
        onSurface:        Color(0xFF1A2B22),
        error:            Color(0xFFD64045),
        outline:          Color(0xFFD4C5B0),
      ),
      scaffoldBackgroundColor: const Color(0xFFF8F4ED),
      appBarTheme: const AppBarTheme(
        backgroundColor: Color(0xFF1B4D3E),
        foregroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
        titleTextStyle: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.w600,
          color: Colors.white,
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: const Color(0xFF1B4D3E),
          foregroundColor: Colors.white,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        ),
      ),
    );
  }

  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      colorScheme: const ColorScheme.dark(
        primary:          Color(0xFF52B788),
        onPrimary:        Color(0xFF0A2218),
        primaryContainer: Color(0xFF1B4D3E),
        secondary:        Color(0xFFD4AF6E),
        onSecondary:      Color(0xFF0D1B2A),
        surface:          Color(0xFF112240),
        onSurface:        Color(0xFFE2EEF4),
        error:            Color(0xFFFF6B6B),
        outline:          Color(0xFF1E3A55),
      ),
      scaffoldBackgroundColor: const Color(0xFF0D1B2A),
      appBarTheme: const AppBarTheme(
        backgroundColor: Color(0xFF0D1B2A),
        foregroundColor: Color(0xFFE2EEF4),
        elevation: 0,
        centerTitle: true,
        titleTextStyle: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.w600,
          color: Color(0xFFE2EEF4),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: const Color(0xFF52B788),
          foregroundColor: const Color(0xFF0A2218),
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        ),
      ),
    );
  }
}
