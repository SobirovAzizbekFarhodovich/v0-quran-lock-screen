import 'package:flutter/material.dart';

abstract class AppSpacing {
  static const xs  =  4.0;
  static const sm  =  8.0;
  static const md  = 12.0;
  static const lg  = 16.0;
  static const xl  = 20.0; // Ekran margini
  static const x2l = 24.0;
  static const x3l = 32.0;
  static const x4l = 48.0;

  static const screenPadding = EdgeInsets.symmetric(horizontal: xl);
  static const cardPadding   = EdgeInsets.all(lg);
}
