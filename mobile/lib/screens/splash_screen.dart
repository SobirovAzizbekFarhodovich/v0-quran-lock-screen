import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../app/colors.dart';
import '../app/text_styles.dart';
import '../core/di/injector.dart';
import '../features/auth/presentation/bloc/auth_bloc.dart';
import '../features/auth/presentation/bloc/auth_state.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> with TickerProviderStateMixin {
  late AnimationController _rotationController;
  late AnimationController _fadeController;
  late AnimationController _slideController;

  String _currentLanguageText = 'English';
  final List<String> _languages = ['English', 'Русский', 'O\'zbek', 'Ўзбек'];
  int _langIndex = 0;
  Timer? _langTimer;

  @override
  void initState() {
    super.initState();

    _rotationController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 10),
    )..repeat();

    _fadeController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    );

    _slideController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );

    // Run animations
    _fadeController.forward();
    _slideController.forward();

    // Language cycling
    _langTimer = Timer.periodic(const Duration(milliseconds: 600), (timer) {
      if (mounted) {
        setState(() {
          _langIndex = (_langIndex + 1) % _languages.length;
          _currentLanguageText = _languages[_langIndex];
        });
      }
    });

    // Navigation after 2.5 seconds
    Timer(const Duration(milliseconds: 2800), _navigateToNext);
  }

  void _navigateToNext() async {
    if (!mounted) return;
    
    // Check auth status
    final authState = context.read<AuthBloc>().state;
    final isAuthenticated = authState is AuthAuthenticated;

    if (isAuthenticated) {
      context.go('/');
      return;
    }

    final prefs = getIt<SharedPreferences>();
    final onboardingCompleted = prefs.getBool('onboarding_completed') ?? false;

    if (onboardingCompleted) {
      context.go('/login');
    } else {
      context.go('/onboarding');
    }
  }

  @override
  void dispose() {
    _rotationController.dispose();
    _fadeController.dispose();
    _slideController.dispose();
    _langTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [
              AppColors.primaryDark,
              AppColors.primary,
              AppColors.primaryMid,
            ],
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
          ),
        ),
        child: Stack(
          children: [
            // Center content
            Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Rotating Islamic Geometric Pattern (painted programmatically)
                  AnimatedBuilder(
                    animation: _rotationController,
                    builder: (context, child) {
                      return Transform.rotate(
                        angle: _rotationController.value * 2 * math.pi,
                        child: CustomPaint(
                          size: const Size(120, 120),
                          painter: IslamicGeometricPainter(),
                        ),
                      );
                    },
                  ),
                  const SizedBox(height: 24),
                  // Bismillah text (fade & slide)
                  FadeTransition(
                    opacity: _fadeController,
                    child: SlideTransition(
                      position: Tween<Offset>(
                        begin: const Offset(0, 0.3),
                        end: Offset.zero,
                      ).animate(CurvedAnimation(
                        parent: _slideController,
                        curve: Curves.easeOutBack,
                      )),
                      child: Column(
                        children: [
                          const Text(
                            'بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ',
                            style: TextStyle(
                              fontFamily: 'Amiri',
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                              color: AppColors.gold,
                            ),
                          ),
                          const SizedBox(height: 12),
                          Text(
                            'Islamic AI',
                            style: AppTextStyles.displayLarge.copyWith(
                              color: Colors.white,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            'Qur\'on ilmiga yo\'lboshchi',
                            style: AppTextStyles.bodyMedium.copyWith(
                              color: Colors.white.withValues(alpha: 0.5),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            // Bottom language cycling text
            Positioned(
              bottom: 48,
              left: 0,
              right: 0,
              child: Center(
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 200),
                  transitionBuilder: (child, animation) {
                    return FadeTransition(opacity: animation, child: child);
                  },
                  child: Text(
                    _currentLanguageText,
                    key: ValueKey<String>(_currentLanguageText),
                    style: AppTextStyles.bodyMedium.copyWith(
                      color: Colors.white.withValues(alpha: 0.7),
                      letterSpacing: 1.0,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// Custom Painter to draw a gorgeous Islamic star mandala pattern
class IslamicGeometricPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = AppColors.gold.withValues(alpha: 0.9)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.0;

    final fillPaint = Paint()
      ..color = AppColors.goldLight.withValues(alpha: 0.15)
      ..style = PaintingStyle.fill;

    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2;

    // Draw concentric circle
    canvas.drawCircle(center, radius * 0.9, paint);
    canvas.drawCircle(center, radius * 0.4, paint);

    // Draw a beautiful 12-point star pattern
    final path = Path();
    const pointsCount = 12;
    for (int i = 0; i < pointsCount; i++) {
      final angle1 = (i * 2 * math.pi) / pointsCount;
      final angle2 = ((i + 5) * 2 * math.pi) / pointsCount; // Star vertices link offset

      final p1 = Offset(
        center.dx + radius * 0.85 * math.cos(angle1),
        center.dy + radius * 0.85 * math.sin(angle1),
      );
      final p2 = Offset(
        center.dx + radius * 0.85 * math.cos(angle2),
        center.dy + radius * 0.85 * math.sin(angle2),
      );

      if (i == 0) {
        path.moveTo(p1.dx, p1.dy);
      } else {
        path.lineTo(p1.dx, p1.dy);
      }
      path.lineTo(p2.dx, p2.dy);
    }
    path.close();
    canvas.drawPath(path, fillPaint);
    canvas.drawPath(path, paint);

    // Add extra lines to make it look like a mosque dome/geometric motif
    for (int i = 0; i < pointsCount; i++) {
      final angle = (i * 2 * math.pi) / pointsCount;
      final outerPoint = Offset(
        center.dx + radius * 0.9 * math.cos(angle),
        center.dy + radius * 0.9 * math.sin(angle),
      );
      canvas.drawLine(center, outerPoint, Paint()..color = AppColors.gold.withValues(alpha: 0.25)..strokeWidth = 1.0);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
