import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../app/colors.dart';
import '../app/text_styles.dart';
import '../app/spacing.dart';
import '../core/di/injector.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final PageController _pageController = PageController();
  int _currentPage = 0;

  final List<OnboardingData> _slides = [
    OnboardingData(
      title: 'Qiroatingizni tekshiring',
      desc: 'Real vaqtda tajweed xatolarini aniqlang va ballingizni oshiring.',
      icon: Icons.mic_rounded,
      iconColor: AppColors.primaryLight,
    ),
    OnboardingData(
      title: 'Ruhiy maslahat oling',
      desc: 'Qur\'on va hadisdan ruhiy holatingizga mos ibrat va dalda toping.',
      icon: Icons.menu_book_rounded,
      iconColor: AppColors.gold,
    ),
    OnboardingData(
      title: 'O\'z tilingizda',
      desc: 'English, Русский, O\'zbek va Ўзбек — barchasida mukammal ishlaydi.',
      icon: Icons.translate_rounded,
      iconColor: AppColors.info,
    ),
  ];

  void _onDone() async {
    final prefs = getIt<SharedPreferences>();
    await prefs.setBool('onboarding_completed', true);
    if (mounted) {
      context.go('/login');
    }
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            // Skip button at top
            Align(
              alignment: Alignment.centerRight,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl, vertical: AppSpacing.sm),
                child: TextButton(
                  onPressed: _onDone,
                  child: Text(
                    'O\'tkazib yuborish',
                    style: AppTextStyles.labelLarge.copyWith(
                      color: isDark ? AppColors.darkText2 : AppColors.textSecondary,
                    ),
                  ),
                ),
              ),
            ),
            // Page view containing slides
            Expanded(
              child: PageView.builder(
                controller: _pageController,
                itemCount: _slides.length,
                onPageChanged: (int page) {
                  setState(() {
                    _currentPage = page;
                  });
                },
                itemBuilder: (context, index) {
                  final slide = _slides[index];
                  return Padding(
                    padding: const EdgeInsets.all(AppSpacing.xl),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        // Custom vector icon shape
                        Container(
                          width: 180,
                          height: 180,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: slide.iconColor.withValues(alpha: 0.12),
                          ),
                          child: Center(
                            child: Icon(
                              slide.icon,
                              size: 80,
                              color: slide.iconColor,
                            ),
                          ),
                        ),
                        const SizedBox(height: AppSpacing.x4l),
                        Text(
                          slide.title,
                          textAlign: TextAlign.center,
                          style: AppTextStyles.displayMedium.copyWith(
                            color: isDark ? AppColors.darkText : AppColors.primary,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.lg),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md),
                          child: Text(
                            slide.desc,
                            textAlign: TextAlign.center,
                            style: AppTextStyles.bodyLarge.copyWith(
                              color: isDark ? AppColors.darkText2 : AppColors.textSecondary,
                              height: 1.45,
                            ),
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
            // Bottom control area
            Padding(
              padding: const EdgeInsets.all(AppSpacing.xl),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Dot indicator
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: List.generate(_slides.length, (index) {
                      final isActive = index == _currentPage;
                      return AnimatedContainer(
                        duration: const Duration(milliseconds: 300),
                        width: isActive ? 24 : 8,
                        height: 8,
                        margin: const EdgeInsets.symmetric(horizontal: AppSpacing.xs),
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(4),
                          color: isActive
                              ? (isDark ? AppColors.primaryLight : AppColors.primary)
                              : (isDark ? AppColors.darkBorder : AppColors.border),
                        ),
                      );
                    }),
                  ),
                  const SizedBox(height: AppSpacing.x2l),
                  // Action buttons
                  AnimatedSwitcher(
                    duration: const Duration(milliseconds: 300),
                    child: _currentPage == _slides.length - 1
                        ? Column(
                            key: const ValueKey('actions_final'),
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              // Primary action button
                              Container(
                                height: 52,
                                decoration: BoxDecoration(
                                  gradient: const LinearGradient(
                                    colors: [AppColors.primary, AppColors.primaryMid],
                                    begin: Alignment.topLeft,
                                    end: Alignment.bottomRight,
                                  ),
                                  borderRadius: BorderRadius.circular(12),
                                  boxShadow: [
                                    BoxShadow(
                                      color: AppColors.primary.withValues(alpha: 0.35),
                                      blurRadius: 20,
                                      offset: const Offset(0, 6),
                                    ),
                                  ],
                                ),
                                child: ElevatedButton(
                                  onPressed: _onDone,
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: Colors.transparent,
                                    shadowColor: Colors.transparent,
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                  ),
                                  child: Text(
                                    'Boshlashga tayyor!',
                                    style: AppTextStyles.labelLarge.copyWith(
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                      fontSize: 16,
                                    ),
                                  ),
                                ),
                              ),
                              const SizedBox(height: AppSpacing.sm),
                              // Secondary action button
                              TextButton(
                                onPressed: _onDone,
                                child: Text(
                                  'Tizimga kirish',
                                  style: AppTextStyles.labelLarge.copyWith(
                                    color: isDark ? AppColors.primaryLight : AppColors.primary,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                            ],
                          )
                        : SizedBox(
                            key: const ValueKey('actions_next'),
                            width: double.infinity,
                            height: 52,
                            child: ElevatedButton(
                              onPressed: () {
                                _pageController.nextPage(
                                  duration: const Duration(milliseconds: 300),
                                  curve: Curves.easeInOut,
                                );
                              },
                              style: ElevatedButton.styleFrom(
                                backgroundColor: isDark ? AppColors.primaryLight : AppColors.primary,
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(12),
                                ),
                              ),
                              child: Text(
                                'Keyingisi',
                                style: AppTextStyles.labelLarge.copyWith(
                                  color: isDark ? AppColors.primaryDark : Colors.white,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                          ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class OnboardingData {
  final String title;
  final String desc;
  final IconData icon;
  final Color iconColor;

  OnboardingData({
    required this.title,
    required this.desc,
    required this.icon,
    required this.iconColor,
  });
}
