import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../app/colors.dart';

/// Floating 3D-style crescent, lanterns and open Quran (reference onboarding hero).
class Islamic3DScene extends StatefulWidget {
  const Islamic3DScene({
    super.key,
    this.height = 260,
    this.compact = false,
  });

  final double height;
  final bool compact;

  @override
  State<Islamic3DScene> createState() => _Islamic3DSceneState();
}

class _Islamic3DSceneState extends State<Islamic3DScene> with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(seconds: 5))..repeat(reverse: true);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: widget.height,
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, child) {
          final t = _controller.value;
          final floatY = math.sin(t * math.pi) * (widget.compact ? 6 : 12);
          final rotateY = (t - 0.5) * 0.12;

          return Stack(
            alignment: Alignment.center,
            clipBehavior: Clip.none,
            children: [
              Transform.translate(
                offset: Offset(0, floatY - 20),
                child: Transform(
                  alignment: Alignment.center,
                  transform: Matrix4.identity()
                    ..setEntry(3, 2, 0.001)
                    ..rotateY(rotateY),
                  child: CustomPaint(
                    size: Size(widget.compact ? 140 : 180, widget.compact ? 140 : 180),
                    painter: _CrescentPainter(),
                  ),
                ),
              ),
              if (!widget.compact) ...[
                Positioned(
                  left: 24,
                  top: 30 + floatY * 0.5,
                  child: _Lantern(tilt: -0.08 + t * 0.06, scale: 0.85),
                ),
                Positioned(
                  right: 24,
                  top: 30 + floatY * 0.5,
                  child: _Lantern(tilt: 0.08 - t * 0.06, scale: 0.85),
                ),
              ],
              Transform.translate(
                offset: Offset(0, 40 + floatY),
                child: Transform(
                  alignment: Alignment.center,
                  transform: Matrix4.identity()
                    ..setEntry(3, 2, 0.0015)
                    ..rotateX(-0.25 + t * 0.08),
                  child: _OpenQuranBook(width: widget.compact ? 100 : 120),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _Lantern extends StatelessWidget {
  const _Lantern({required this.tilt, required this.scale});

  final double tilt;
  final double scale;

  @override
  Widget build(BuildContext context) {
    return Transform.scale(
      scale: scale,
      child: Transform.rotate(
        angle: tilt,
        child: Container(
          width: 36,
          height: 52,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                AppColors.goldLight.withOpacity(0.95),
                AppColors.gold.withOpacity(0.85),
              ],
            ),
            boxShadow: [
              BoxShadow(
                color: AppColors.gold.withOpacity(0.45),
                blurRadius: 16,
                spreadRadius: 1,
              ),
            ],
          ),
          child: Column(
            children: [
              Container(
                margin: const EdgeInsets.only(top: 4),
                width: 14,
                height: 6,
                decoration: BoxDecoration(
                  color: AppColors.goldDark,
                  borderRadius: BorderRadius.circular(3),
                ),
              ),
              Expanded(
                child: Center(
                  child: Icon(Icons.wb_incandescent_outlined, size: 16, color: AppColors.goldDark.withOpacity(0.7)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _OpenQuranBook extends StatelessWidget {
  const _OpenQuranBook({required this.width});

  final double width;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      height: width * 0.72,
      child: Stack(
        alignment: Alignment.center,
        children: [
          Container(
            width: width,
            height: width * 0.65,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(10),
              gradient: const LinearGradient(
                colors: [Color(0xFF1E4D3A), Color(0xFF0F2E24)],
              ),
              boxShadow: [
                BoxShadow(
                  color: AppColors.neonTeal.withOpacity(0.35),
                  blurRadius: 24,
                  offset: const Offset(0, 10),
                ),
              ],
              border: Border.all(color: AppColors.gold.withOpacity(0.5), width: 1.2),
            ),
            child: Center(
              child: Text(
                '﷽',
                style: TextStyle(
                  fontFamily: 'Amiri',
                  fontSize: width * 0.22,
                  color: AppColors.goldLight,
                  shadows: [
                    Shadow(color: AppColors.gold.withOpacity(0.8), blurRadius: 12),
                  ],
                ),
              ),
            ),
          ),
          Positioned(
            bottom: 0,
            child: Container(
              width: width * 0.7,
              height: 8,
              decoration: BoxDecoration(
                color: AppColors.goldDark,
                borderRadius: BorderRadius.circular(4),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _CrescentPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final outer = size.width * 0.42;

    final gold = Paint()
      ..shader = RadialGradient(
        colors: [AppColors.goldLight, AppColors.gold, AppColors.goldDark],
      ).createShader(Rect.fromCircle(center: center, radius: outer));

    canvas.saveLayer(Rect.fromLTWH(0, 0, size.width, size.height), Paint());
    canvas.drawCircle(center, outer, gold);
    final cut = Paint()
      ..blendMode = BlendMode.srcOut
      ..color = Colors.white;
    canvas.drawCircle(center.translate(outer * 0.45, -outer * 0.08), outer * 0.82, cut);
    canvas.restore();

    final starPaint = Paint()..color = AppColors.goldLight;
    _drawStar(canvas, center.translate(-outer * 0.15, outer * 0.05), outer * 0.12, starPaint);
  }

  void _drawStar(Canvas canvas, Offset center, double radius, Paint paint) {
    const points = 5;
    final path = Path();
    for (var i = 0; i < points * 2; i++) {
      final r = i.isEven ? radius : radius * 0.45;
      final angle = (i * math.pi / points) - math.pi / 2;
      final p = Offset(center.dx + r * math.cos(angle), center.dy + r * math.sin(angle));
      if (i == 0) {
        path.moveTo(p.dx, p.dy);
      } else {
        path.lineTo(p.dx, p.dy);
      }
    }
    path.close();
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
