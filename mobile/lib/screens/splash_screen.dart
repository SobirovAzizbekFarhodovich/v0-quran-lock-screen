import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../app/colors.dart';
import '../app/text_styles.dart';
import '../core/di/injector.dart';
import '../features/auth/presentation/bloc/auth_bloc.dart';
import '../features/auth/presentation/bloc/auth_state.dart';
import '../widgets/cosmic_background.dart';
import '../widgets/islamic_3d_scene.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> with TickerProviderStateMixin {
  late AnimationController _fadeController;
  late AnimationController _scaleController;
  String _langLabel = 'English';
  final _languages = ['English', 'Русский', 'O\'zbek', 'Ўзбек'];
  int _langIndex = 0;
  Timer? _langTimer;

  @override
  void initState() {
    super.initState();
    _fadeController = AnimationController(vsync: this, duration: const Duration(milliseconds: 1200))..forward();
    _scaleController = AnimationController(vsync: this, duration: const Duration(milliseconds: 900))..forward();
    _langTimer = Timer.periodic(const Duration(milliseconds: 650), (_) {
      if (!mounted) return;
      setState(() {
        _langIndex = (_langIndex + 1) % _languages.length;
        _langLabel = _languages[_langIndex];
      });
    });
    Timer(const Duration(milliseconds: 2800), _navigate);
  }

  Future<void> _navigate() async {
    if (!mounted) return;
    final authState = context.read<AuthBloc>().state;
    if (authState is AuthAuthenticated) {
      context.go('/');
      return;
    }
    final prefs = getIt<SharedPreferences>();
    final done = prefs.getBool('onboarding_completed') ?? false;
    context.go(done ? '/login' : '/onboarding');
  }

  @override
  void dispose() {
    _fadeController.dispose();
    _scaleController.dispose();
    _langTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: CosmicBackground(
        showMosque: false,
        child: Center(
          child: FadeTransition(
            opacity: CurvedAnimation(parent: _fadeController, curve: Curves.easeOut),
            child: ScaleTransition(
              scale: Tween<double>(begin: 0.92, end: 1).animate(
                CurvedAnimation(parent: _scaleController, curve: Curves.easeOutBack),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Islamic3DScene(height: 220, compact: true),
                  const SizedBox(height: 28),
                  Text(
                    'بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ',
                    style: AppTextStyles.arabicMedium.copyWith(
                      color: AppColors.goldLight,
                      fontWeight: FontWeight.bold,
                      shadows: [Shadow(color: AppColors.gold.withOpacity(0.6), blurRadius: 16)],
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'Quran Reminder AI',
                    style: AppTextStyles.displayLarge.copyWith(
                      color: AppColors.darkText,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Qur\'on ilmiga yo\'lboshchi',
                    style: AppTextStyles.bodyMedium.copyWith(color: AppColors.darkText2),
                  ),
                  const SizedBox(height: 48),
                  SizedBox(
                    width: 36,
                    height: 36,
                    child: CircularProgressIndicator(
                      strokeWidth: 2.5,
                      color: AppColors.neonTeal.withOpacity(0.85),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    _langLabel,
                    style: AppTextStyles.labelSmall.copyWith(color: AppColors.darkText2),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
