import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/material.dart';

import '../app/colors.dart';

/// Full-screen midnight gradient with stars and mosque silhouette (reference UI).
class CosmicBackground extends StatelessWidget {
  const CosmicBackground({
    super.key,
    required this.child,
    this.showMosque = true,
    this.padding = EdgeInsets.zero,
  });

  final Widget child;
  final bool showMosque;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            AppColors.midnightDeep,
            AppColors.midnight,
            AppColors.midnightSurface,
          ],
          stops: [0.0, 0.45, 1.0],
        ),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          CustomPaint(painter: _StarFieldPainter(seed: 42)),
          if (showMosque)
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              height: 140,
              child: CustomPaint(painter: _MosqueSilhouettePainter()),
            ),
          Positioned(
            top: -80,
            right: -40,
            child: Container(
              width: 220,
              height: 220,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(
                  colors: [
                    AppColors.neonTeal.withOpacity(0.12),
                    Colors.transparent,
                  ],
                ),
              ),
            ),
          ),
          Padding(padding: padding, child: child),
        ],
      ),
    );
  }
}

class GlassContainer extends StatelessWidget {
  const GlassContainer({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.borderRadius = 20,
    this.borderColor,
    this.onTap,
  });

  final Widget child;
  final EdgeInsets padding;
  final double borderRadius;
  final Color? borderColor;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final border = borderColor ?? Colors.white.withOpacity(0.12);
    final content = ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
        child: Container(
          padding: padding,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(borderRadius),
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                Colors.white.withOpacity(0.14),
                Colors.white.withOpacity(0.05),
              ],
            ),
            border: Border.all(color: border, width: 1),
            boxShadow: [
              BoxShadow(
                color: AppColors.neonTeal.withOpacity(0.06),
                blurRadius: 24,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: child,
        ),
      ),
    );

    if (onTap == null) return content;
    return Material(
      color: Colors.transparent,
      child: InkWell(onTap: onTap, borderRadius: BorderRadius.circular(borderRadius), child: content),
    );
  }
}

class _StarFieldPainter extends CustomPainter {
  _StarFieldPainter({required this.seed});

  final int seed;

  @override
  void paint(Canvas canvas, Size size) {
    final random = math.Random(seed);
    final paint = Paint()..style = PaintingStyle.fill;
    for (var i = 0; i < 80; i++) {
      final x = random.nextDouble() * size.width;
      final y = random.nextDouble() * size.height * 0.85;
      final r = random.nextDouble() * 1.4 + 0.3;
      paint.color = Colors.white.withOpacity(0.08 + random.nextDouble() * 0.35);
      canvas.drawCircle(Offset(x, y), r, paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _MosqueSilhouettePainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          Colors.transparent,
          AppColors.neonTeal.withOpacity(0.08),
          Colors.black.withOpacity(0.55),
        ],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height));

    final path = Path();
    path.moveTo(0, size.height);
    path.lineTo(0, size.height * 0.55);

    final domeCount = 5;
    final step = size.width / domeCount;
    for (var i = 0; i < domeCount; i++) {
      final cx = step * i + step / 2;
      path.quadraticBezierTo(cx - step * 0.2, size.height * 0.35, cx, size.height * 0.42);
      path.quadraticBezierTo(cx + step * 0.2, size.height * 0.35, cx + step * 0.45, size.height * 0.55);
    }
    path.lineTo(size.width, size.height * 0.6);
    path.lineTo(size.width, size.height);
    path.close();
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
