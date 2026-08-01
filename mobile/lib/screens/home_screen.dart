import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import '../app/colors.dart';
import '../app/spacing.dart';
import '../app/text_styles.dart';
import '../features/auth/presentation/bloc/auth_bloc.dart';
import '../features/auth/presentation/bloc/auth_state.dart';
import '../widgets/cosmic_background.dart';
import '../widgets/islamic_3d_scene.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return CosmicBackground(
      showMosque: true,
      child: SafeArea(
        child: BlocBuilder<AuthBloc, AuthState>(
          builder: (context, authState) {
            final userName = authState is AuthAuthenticated ? authState.user.name.split(' ').first : 'Do\'stim';

            return CustomScrollView(
              slivers: [
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(AppSpacing.xl, AppSpacing.md, AppSpacing.xl, 0),
                    child: Row(
                      children: [
                        _GlassIconButton(icon: Icons.menu_rounded, onTap: () => context.push('/settings')),
                        const Spacer(),
                        _GlassIconButton(
                          icon: Icons.notifications_none_rounded,
                          onTap: () {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('Yangi bildirishnomalar yo\'q')),
                            );
                          },
                        ),
                        const SizedBox(width: 10),
                        GestureDetector(
                          onTap: () => context.go('/profile'),
                          child: CircleAvatar(
                            radius: 22,
                            backgroundColor: AppColors.neonTeal.withOpacity(0.25),
                            child: Text(
                              userName.isNotEmpty ? userName[0].toUpperCase() : '?',
                              style: AppTextStyles.labelLarge.copyWith(color: AppColors.neonCyan),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const SizedBox(height: AppSpacing.lg),
                        Text(
                          'Assalamu alaykum',
                          style: AppTextStyles.bodyMedium.copyWith(color: AppColors.darkText2),
                        ),
                        Text(
                          userName,
                          style: AppTextStyles.displayMedium.copyWith(
                            color: AppColors.darkText,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.x2l),
                        GlassContainer(
                          padding: const EdgeInsets.all(AppSpacing.lg),
                          borderRadius: 24,
                          onTap: () => context.go('/spiritual'),
                          child: Stack(
                            children: [
                              const Positioned(
                                right: -8,
                                top: -12,
                                child: Islamic3DScene(height: 100, compact: true),
                              ),
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                                    decoration: BoxDecoration(
                                      color: AppColors.gold.withOpacity(0.2),
                                      borderRadius: BorderRadius.circular(20),
                                      border: Border.all(color: AppColors.gold.withOpacity(0.35)),
                                    ),
                                    child: Text(
                                      'Tadabbur AI',
                                      style: AppTextStyles.labelMedium.copyWith(color: AppColors.goldLight),
                                    ),
                                  ),
                                  const SizedBox(height: 12),
                                  Text(
                                    'Qur\'on va Hadisdan\nruhiy javob oling',
                                    style: AppTextStyles.headlineMedium.copyWith(
                                      color: AppColors.darkText,
                                      height: 1.25,
                                    ),
                                  ),
                                  const SizedBox(height: 14),
                                  Row(
                                    children: [
                                      Text(
                                        'Boshlash',
                                        style: AppTextStyles.labelLarge.copyWith(color: AppColors.neonCyan),
                                      ),
                                      const SizedBox(width: 6),
                                      Icon(Icons.arrow_forward_rounded, size: 18, color: AppColors.neonCyan),
                                    ],
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: AppSpacing.xl),
                        Text(
                          'Imkoniyatlar',
                          style: AppTextStyles.headlineMedium.copyWith(
                            color: AppColors.darkText,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.md),
                        Row(
                          children: [
                            Expanded(
                              child: _FeatureTile(
                                icon: Icons.mic_rounded,
                                label: 'Qiroat',
                                onTap: () => context.go('/recitation'),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: _FeatureTile(
                                icon: Icons.auto_stories_rounded,
                                label: 'Tarix',
                                onTap: () => context.push('/sessions'),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: _FeatureTile(
                                icon: Icons.favorite_rounded,
                                label: 'Taskin',
                                onTap: () => context.go('/spiritual'),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: AppSpacing.x2l),
                        Text(
                          'Haftalik natija',
                          style: AppTextStyles.headlineMedium.copyWith(
                            color: AppColors.darkText,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.sm),
                        GlassContainer(
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceAround,
                            children: [
                              _StatChip(value: '7', label: 'Seriya', icon: Icons.local_fire_department_rounded),
                              _StatChip(value: '94%', label: 'Aniqlik', icon: Icons.star_rounded),
                              _StatChip(value: '12', label: 'Sessiya', icon: Icons.menu_book_rounded),
                            ],
                          ),
                        ),
                        const SizedBox(height: AppSpacing.x2l),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              'So\'nggi qiroatlar',
                              style: AppTextStyles.headlineMedium.copyWith(
                                color: AppColors.darkText,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            TextButton(
                              onPressed: () => context.push('/sessions'),
                              child: Text('Barchasi', style: AppTextStyles.labelLarge.copyWith(color: AppColors.neonTeal)),
                            ),
                          ],
                        ),
                        GlassContainer(
                          padding: EdgeInsets.zero,
                          child: Column(
                            children: [
                              _SessionRow(title: 'Al-Fatihah', subtitle: 'Bugun', score: '96.5'),
                              Divider(height: 1, color: Colors.white.withOpacity(0.08)),
                              _SessionRow(title: 'Al-Baqara', subtitle: 'Kecha', score: '88.0'),
                            ],
                          ),
                        ),
                        const SizedBox(height: 100),
                      ],
                    ),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _GlassIconButton extends StatelessWidget {
  const _GlassIconButton({required this.icon, required this.onTap});

  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GlassContainer(
      padding: const EdgeInsets.all(10),
      borderRadius: 14,
      onTap: onTap,
      child: Icon(icon, color: AppColors.darkText, size: 22),
    );
  }
}

class _FeatureTile extends StatelessWidget {
  const _FeatureTile({required this.icon, required this.label, required this.onTap});

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GlassContainer(
      padding: const EdgeInsets.symmetric(vertical: 18, horizontal: 8),
      borderRadius: 18,
      onTap: onTap,
      child: Column(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: LinearGradient(
                colors: [
                  AppColors.neonTeal.withOpacity(0.35),
                  AppColors.neonCyan.withOpacity(0.15),
                ],
              ),
              boxShadow: [
                BoxShadow(color: AppColors.neonTeal.withOpacity(0.25), blurRadius: 12),
              ],
            ),
            child: Icon(icon, color: AppColors.neonCyan, size: 26),
          ),
          const SizedBox(height: 10),
          Text(label, style: AppTextStyles.labelMedium.copyWith(color: AppColors.darkText)),
        ],
      ),
    );
  }
}

class _StatChip extends StatelessWidget {
  const _StatChip({required this.value, required this.label, required this.icon});

  final String value;
  final String label;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Icon(icon, color: AppColors.gold, size: 20),
        const SizedBox(height: 6),
        Text(
          value,
          style: AppTextStyles.headlineMedium.copyWith(
            color: AppColors.neonCyan,
            fontWeight: FontWeight.bold,
          ),
        ),
        Text(label, style: AppTextStyles.labelSmall.copyWith(color: AppColors.darkText2)),
      ],
    );
  }
}

class _SessionRow extends StatelessWidget {
  const _SessionRow({required this.title, required this.subtitle, required this.score});

  final String title;
  final String subtitle;
  final String score;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: AppColors.neonTeal.withOpacity(0.15),
            ),
            child: const Icon(Icons.check_circle_outline, color: AppColors.neonTeal, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: AppTextStyles.labelLarge.copyWith(color: AppColors.darkText)),
                Text(subtitle, style: AppTextStyles.labelSmall.copyWith(color: AppColors.darkText2)),
              ],
            ),
          ),
          Text(
            '$score%',
            style: AppTextStyles.labelLarge.copyWith(color: AppColors.neonTeal, fontWeight: FontWeight.bold),
          ),
        ],
      ),
    );
  }
}
