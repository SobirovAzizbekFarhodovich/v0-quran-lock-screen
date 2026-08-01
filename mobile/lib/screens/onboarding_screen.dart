import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../app/colors.dart';
import '../app/spacing.dart';
import '../app/text_styles.dart';
import '../core/di/injector.dart';
import '../widgets/cosmic_background.dart';
import '../widgets/glow_button.dart';
import '../widgets/islamic_3d_scene.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final PageController _pageController = PageController();
  int _currentPage = 0;

  final _slides = const [
    _Slide(
      title: 'Qur\'on bilan bog\'laning',
      desc: 'Real vaqtda qiroatingizni tahlil qiling, tajweed xatolarini aniqlang.',
    ),
    _Slide(
      title: 'Ruhiy taskin',
      desc: 'Savolingizga Qur\'on va sahih hadislardan dalillar bilan javob oling.',
    ),
    _Slide(
      title: 'O\'z tilingizda',
      desc: 'O\'zbek (lotin/kirill), ingliz va rus tillarida qulay interfeys.',
    ),
  ];

  Future<void> _finish() async {
    final prefs = getIt<SharedPreferences>();
    await prefs.setBool('onboarding_completed', true);
    if (mounted) context.go('/login');
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: CosmicBackground(
        showMosque: true,
        child: SafeArea(
          child: Column(
            children: [
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  onPressed: _finish,
                  child: Text('O\'tkazish', style: AppTextStyles.labelLarge.copyWith(color: AppColors.darkText2)),
                ),
              ),
              Expanded(
                child: PageView.builder(
                  controller: _pageController,
                  itemCount: _slides.length,
                  onPageChanged: (p) => setState(() => _currentPage = p),
                  itemBuilder: (context, index) {
                    final slide = _slides[index];
                    return Padding(
                      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
                      child: Column(
                        children: [
                          const Islamic3DScene(height: 280),
                          const SizedBox(height: AppSpacing.x2l),
                          Text(
                            slide.title,
                            textAlign: TextAlign.center,
                            style: AppTextStyles.displayMedium.copyWith(
                              color: AppColors.darkText,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: AppSpacing.lg),
                          Text(
                            slide.desc,
                            textAlign: TextAlign.center,
                            style: AppTextStyles.bodyLarge.copyWith(
                              color: AppColors.darkText2,
                              height: 1.5,
                            ),
                          ),
                        ],
                      ),
                    );
                  },
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(AppSpacing.xl),
                child: Column(
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: List.generate(_slides.length, (i) {
                        final active = i == _currentPage;
                        return AnimatedContainer(
                          duration: const Duration(milliseconds: 250),
                          margin: const EdgeInsets.symmetric(horizontal: 4),
                          width: active ? 28 : 8,
                          height: 8,
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(4),
                            color: active ? AppColors.neonTeal : Colors.white.withOpacity(0.2),
                          ),
                        );
                      }),
                    ),
                    const SizedBox(height: AppSpacing.x2l),
                    if (_currentPage == _slides.length - 1) ...[
                      GlowPrimaryButton(label: 'Boshlash', onPressed: _finish),
                      const SizedBox(height: 12),
                      TextButton(
                        onPressed: _finish,
                        child: Text(
                          'Tizimga kirish',
                          style: AppTextStyles.labelLarge.copyWith(color: AppColors.neonCyan),
                        ),
                      ),
                    ] else
                      Center(
                        child: GlowCircleButton(
                          icon: Icons.arrow_forward_rounded,
                          size: 64,
                          onPressed: () => _pageController.nextPage(
                            duration: const Duration(milliseconds: 350),
                            curve: Curves.easeOutCubic,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Slide {
  const _Slide({required this.title, required this.desc});
  final String title;
  final String desc;
}
