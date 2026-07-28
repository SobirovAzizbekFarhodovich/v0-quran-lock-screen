import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';
import '../app/colors.dart';
import '../app/text_styles.dart';
import '../app/spacing.dart';
import '../features/auth/presentation/bloc/auth_bloc.dart';
import '../features/auth/presentation/bloc/auth_state.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    final cardBgColor = isDark ? AppColors.darkSurface : Colors.white;
    final textColor = isDark ? AppColors.darkText : AppColors.textPrimary;
    final textSecondaryColor = isDark ? AppColors.darkText2 : AppColors.textSecondary;
    final shadowColor = isDark ? Colors.transparent : Colors.black.withValues(alpha: 0.04);
    final borderColor = isDark ? AppColors.darkBorder : AppColors.border;

    return Scaffold(
      backgroundColor: isDark ? AppColors.darkBackground : AppColors.background,
      body: BlocBuilder<AuthBloc, AuthState>(
        builder: (context, authState) {
          String userName = 'Muhammad';
          if (authState is AuthAuthenticated) {
            userName = authState.user.name;
          }

          return SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl, vertical: AppSpacing.md),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // APP BAR / HEADER
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Assalamu alaykum,',
                            style: AppTextStyles.bodyMedium.copyWith(
                              color: textSecondaryColor,
                            ),
                          ),
                          Text(
                            '$userName 👋',
                            style: AppTextStyles.headlineLarge.copyWith(
                              color: textColor,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                      // Notification bell icon
                      Container(
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: cardBgColor,
                          border: Border.all(color: borderColor),
                        ),
                        child: IconButton(
                          icon: Icon(Icons.notifications_none_rounded, color: isDark ? AppColors.primaryLight : AppColors.primary),
                          onPressed: () {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('Hozircha yangi bildirishnomalar yo\'q.')),
                            );
                          },
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.md),

                  // Gold Target Goal Chip
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                      decoration: BoxDecoration(
                        color: isDark ? AppColors.goldDark.withValues(alpha: 0.3) : AppColors.goldLight.withValues(alpha: 0.35),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(
                          color: isDark ? AppColors.gold.withValues(alpha: 0.3) : AppColors.gold.withValues(alpha: 0.6),
                        ),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.stars_rounded, color: AppColors.gold, size: 18),
                          const SizedBox(width: 6),
                          Text(
                            'Kunlik maqsad: 10 daqiqa',
                            style: AppTextStyles.labelMedium.copyWith(
                              color: isDark ? AppColors.goldLight : AppColors.goldDark,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: AppSpacing.x2l),

                  // SERVICE CARDS (2 columns)
                  Row(
                    children: [
                      // Recitation checking
                      Expanded(
                        child: _buildServiceCard(
                          title: 'Qiroat\nTekshiruvi',
                          desc: 'Bugun o\'qidingmi?',
                          cta: 'Boshlash →',
                          icon: Icons.mic_rounded,
                          gradient: const LinearGradient(
                            colors: [Color(0xFF0F7A50), Color(0xFF1B4D3E)],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                          onTap: () => context.go('/recitation'),
                          shadowColor: AppColors.primary.withValues(alpha: 0.2),
                        ),
                      ),
                      const SizedBox(width: AppSpacing.md),
                      // Spiritual AI companion
                      Expanded(
                        child: _buildServiceCard(
                          title: 'Ruhiy\nTaskin',
                          desc: 'Holingizni yozing',
                          cta: 'So\'rash →',
                          icon: Icons.favorite_rounded,
                          gradient: const LinearGradient(
                            colors: [Color(0xFF7A5C2A), Color(0xFFC9A96E)],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                          onTap: () => context.go('/spiritual'),
                          shadowColor: AppColors.gold.withValues(alpha: 0.2),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.x2l),

                  // WEEKLY RESULTS / STATISTICS CARD
                  Text(
                    '📊 Haftalik natijalar',
                    style: AppTextStyles.headlineMedium.copyWith(
                      color: isDark ? AppColors.darkText : AppColors.primary,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: AppSpacing.sm),
                  Container(
                    decoration: BoxDecoration(
                      color: cardBgColor,
                      borderRadius: BorderRadius.circular(16),
                      boxShadow: [
                        BoxShadow(
                          color: shadowColor,
                          blurRadius: 10,
                          offset: const Offset(0, 4),
                        ),
                      ],
                      border: Border.all(color: borderColor),
                    ),
                    padding: const EdgeInsets.all(AppSpacing.lg),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceAround,
                      children: [
                        _buildStatItem('🔥 7 kun', 'Seriya', isDark),
                        _buildStatItem('⭐ 94.2', 'O\'rtacha', isDark),
                        _buildStatItem('📖 42', 'So\'z', isDark),
                      ],
                    ),
                  ),
                  const SizedBox(height: AppSpacing.x2l),

                  // RECENT RECITE SESSIONS
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'So\'nggi Qiroatlar',
                        style: AppTextStyles.headlineMedium.copyWith(
                          color: isDark ? AppColors.darkText : AppColors.primary,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      TextButton(
                        onPressed: () => context.push('/sessions'),
                        child: Text(
                          'Barchasi',
                          style: AppTextStyles.labelLarge.copyWith(
                            color: isDark ? AppColors.primaryLight : AppColors.primary,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.xs),

                  // Recent Sessions List view / card list
                  Container(
                    decoration: BoxDecoration(
                      color: cardBgColor,
                      borderRadius: BorderRadius.circular(16),
                      boxShadow: [
                        BoxShadow(
                          color: shadowColor,
                          blurRadius: 10,
                          offset: const Offset(0, 4),
                        ),
                      ],
                      border: Border.all(color: borderColor),
                    ),
                    child: ListView(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      children: [
                        _buildRecentSessionItem('Al-Fatihah', '94.5', 'Bugun', isDark),
                        Divider(height: 1, color: borderColor),
                        _buildRecentSessionItem('Al-Baqara', '88.0', 'Kecha', isDark),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildServiceCard({
    required String title,
    required String desc,
    required String cta,
    required IconData icon,
    required Gradient gradient,
    required VoidCallback onTap,
    required Color shadowColor,
  }) {
    return Container(
      height: 195,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        gradient: gradient,
        boxShadow: [
          BoxShadow(
            color: shadowColor,
            blurRadius: 12,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(20),
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                // Top circular icon container
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: Colors.white.withValues(alpha: 0.18),
                  ),
                  child: Icon(
                    icon,
                    color: Colors.white,
                    size: 24,
                  ),
                ),
                // Text block
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: AppTextStyles.labelLarge.copyWith(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                        height: 1.25,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      desc,
                      style: AppTextStyles.labelSmall.copyWith(
                        color: Colors.white.withValues(alpha: 0.8),
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      cta,
                      style: AppTextStyles.labelMedium.copyWith(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStatItem(String value, String label, bool isDark) {
    return Column(
      children: [
        Text(
          value,
          style: AppTextStyles.headlineMedium.copyWith(
            color: isDark ? AppColors.goldLight : AppColors.primary,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: AppTextStyles.labelSmall.copyWith(
            color: isDark ? AppColors.darkText2 : AppColors.textTertiary,
          ),
        ),
      ],
    );
  }

  Widget _buildRecentSessionItem(String surahName, String score, String time, bool isDark) {
    final textColor = isDark ? AppColors.darkText : AppColors.textPrimary;
    final textSecondaryColor = isDark ? AppColors.darkText2 : AppColors.textSecondary;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: 14),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: AppColors.primaryLight.withValues(alpha: 0.1),
            ),
            child: const Icon(Icons.check_circle_outline_rounded, color: AppColors.success, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  surahName,
                  style: AppTextStyles.labelLarge.copyWith(color: textColor, fontWeight: FontWeight.bold),
                ),
                Text(
                  time,
                  style: AppTextStyles.labelSmall.copyWith(color: textSecondaryColor),
                ),
              ],
            ),
          ),
          Text(
            '$score ball',
            style: AppTextStyles.labelLarge.copyWith(
              color: AppColors.success,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
}
