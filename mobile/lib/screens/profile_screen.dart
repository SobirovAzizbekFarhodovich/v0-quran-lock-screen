import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import '../app/colors.dart';
import '../app/text_styles.dart';
import '../app/spacing.dart';
import '../features/auth/presentation/bloc/auth_bloc.dart';
import '../features/auth/presentation/bloc/auth_event.dart';
import '../features/auth/presentation/bloc/auth_state.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  final _nameController = TextEditingController();
  String _selectedLanguage = 'uz';

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    
    final cardBg = isDark ? AppColors.darkSurface : Colors.white;
    final textColor = isDark ? AppColors.darkText : AppColors.textPrimary;
    final textSecondary = isDark ? AppColors.darkText2 : AppColors.textSecondary;
    final borderColor = isDark ? AppColors.darkBorder : AppColors.border;

    return BlocConsumer<AuthBloc, AuthState>(
      listener: (context, state) {
        if (state is AuthUnauthenticated) {
          context.go('/login');
        } else if (state is AuthError) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(state.message), backgroundColor: AppColors.error),
          );
        }
      },
      builder: (context, state) {
        if (state is AuthLoading) {
          return Scaffold(
            backgroundColor: isDark ? AppColors.darkBackground : AppColors.background,
            body: const Center(child: CircularProgressIndicator()),
          );
        }

        if (state is AuthAuthenticated) {
          final user = state.user;
          
          if (_nameController.text.isEmpty) {
            _nameController.text = user.name;
            _selectedLanguage = user.language;
          }

          return Scaffold(
            backgroundColor: isDark ? AppColors.darkBackground : AppColors.background,
            appBar: AppBar(
              title: const Text('Profil'),
              backgroundColor: isDark ? AppColors.darkBackground : AppColors.primary,
              foregroundColor: Colors.white,
              elevation: 0,
            ),
            body: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl, vertical: AppSpacing.xl),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // PROFILE AVATAR BLOCK
                  Center(
                    child: Column(
                      children: [
                        Container(
                          width: 86,
                          height: 86,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            border: Border.all(
                              color: isDark ? AppColors.primaryLight : AppColors.primary,
                              width: 3,
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.05),
                                blurRadius: 10,
                              )
                            ],
                          ),
                          child: CircleAvatar(
                            backgroundColor: AppColors.gold.withOpacity(0.1),
                            child: Text(
                              user.name.isNotEmpty ? user.name[0].toUpperCase() : 'A',
                              style: AppTextStyles.displayLarge.copyWith(
                                color: isDark ? AppColors.goldLight : AppColors.primary,
                                fontSize: 32,
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        // Display name (Playfair text)
                        Text(
                          user.name,
                          style: AppTextStyles.headlineLarge.copyWith(
                            color: textColor,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        // Email (Nunito light)
                        Text(
                          user.email,
                          style: AppTextStyles.bodyMedium.copyWith(
                            color: textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: AppSpacing.x2l),

                  // PROFILE STATS SUMMARY
                  Container(
                    decoration: BoxDecoration(
                      color: cardBg,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: borderColor),
                    ),
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceAround,
                      children: [
                        _buildStatItem('47', 'Sessiya', isDark),
                        _buildStatItem('94.2', 'O\'rtacha', isDark),
                        _buildStatItem('12', 'Seriya', isDark),
                      ],
                    ),
                  ),
                  const SizedBox(height: AppSpacing.x2l),

                  // SETTINGS LIST CARD
                  Container(
                    decoration: BoxDecoration(
                      color: cardBg,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: borderColor),
                    ),
                    child: Column(
                      children: [
                        // Edit name
                        ListTile(
                          leading: Icon(Icons.person_outline_rounded, color: isDark ? AppColors.primaryLight : AppColors.primary),
                          title: Text(
                            'Ismni o\'zgartirish',
                            style: AppTextStyles.labelLarge.copyWith(color: textColor),
                          ),
                          trailing: Icon(Icons.arrow_forward_ios_rounded, size: 16, color: textSecondary),
                          onTap: () => _showNameEditDialog(context, isDark),
                        ),
                        Divider(height: 1, color: borderColor),
                        
                        // Language Selector List item
                        ListTile(
                          leading: Icon(Icons.language_rounded, color: isDark ? AppColors.primaryLight : AppColors.primary),
                          title: Text(
                            'Til',
                            style: AppTextStyles.labelLarge.copyWith(color: textColor),
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                _selectedLanguage == 'uz'
                                    ? 'O\'zbek'
                                    : (_selectedLanguage == 'uzk' ? 'Ўзбек' : (_selectedLanguage == 'ru' ? 'Русский' : 'English')),
                                style: AppTextStyles.bodyMedium.copyWith(color: textSecondary),
                              ),
                              const SizedBox(width: 8),
                              Icon(Icons.arrow_forward_ios_rounded, size: 16, color: textSecondary),
                            ],
                          ),
                          onTap: () => _showLanguageDialog(context, isDark),
                        ),
                        Divider(height: 1, color: borderColor),

                        // Theme switch
                        ListTile(
                          leading: Icon(Icons.dark_mode_outlined, color: isDark ? AppColors.primaryLight : AppColors.primary),
                          title: Text(
                            'Tungi mavzu',
                            style: AppTextStyles.labelLarge.copyWith(color: textColor),
                          ),
                          trailing: Switch(
                            value: isDark,
                            activeColor: AppColors.primaryLight,
                            onChanged: (val) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('Mavzu sozlamalari tizim tomonidan boshqariladi.')),
                              );
                            },
                          ),
                        ),
                        Divider(height: 1, color: borderColor),

                        // Security
                        ListTile(
                          leading: Icon(Icons.lock_outline_rounded, color: isDark ? AppColors.primaryLight : AppColors.primary),
                          title: Text(
                            'Xavfsizlik',
                            style: AppTextStyles.labelLarge.copyWith(color: textColor),
                          ),
                          trailing: Icon(Icons.arrow_forward_ios_rounded, size: 16, color: textSecondary),
                          onTap: () {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('Xavfsizlik sozlamalari yoqilgan.')),
                            );
                          },
                        ),
                        Divider(height: 1, color: borderColor),

                        // Terms
                        ListTile(
                          leading: Icon(Icons.description_outlined, color: isDark ? AppColors.primaryLight : AppColors.primary),
                          title: Text(
                            'Foydalanish shartlari',
                            style: AppTextStyles.labelLarge.copyWith(color: textColor),
                          ),
                          trailing: Icon(Icons.arrow_forward_ios_rounded, size: 16, color: textSecondary),
                          onTap: () {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('Foydalanish shartlari ko\'rish...')),
                            );
                          },
                        ),
                        Divider(height: 1, color: borderColor),

                        // Version
                        ListTile(
                          leading: Icon(Icons.info_outline_rounded, color: isDark ? AppColors.primaryLight : AppColors.primary),
                          title: Text(
                            'Versiya',
                            style: AppTextStyles.labelLarge.copyWith(color: textColor),
                          ),
                          trailing: Text(
                            '1.0.0',
                            style: AppTextStyles.bodyMedium.copyWith(color: textSecondary),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: AppSpacing.x3l),

                  // LOGOUT BUTTON (Red outlined/gradient styled)
                  OutlinedButton.icon(
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.error,
                      side: const BorderSide(color: AppColors.error, width: 1.5),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    icon: const Icon(Icons.logout_rounded),
                    label: Text(
                      'Tizimdan chiqish',
                      style: AppTextStyles.labelLarge.copyWith(
                        color: AppColors.error,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    onPressed: () {
                      context.read<AuthBloc>().add(LogoutEvent());
                    },
                  ),
                ],
              ),
            ),
          );
        }

        return Scaffold(
          backgroundColor: isDark ? AppColors.darkBackground : AppColors.background,
          body: const Center(child: Text('Tizimga kiring')),
        );
      },
    );
  }

  Widget _buildStatItem(String value, String label, bool isDark) {
    return Column(
      children: [
        Text(
          value,
          style: AppTextStyles.headlineLarge.copyWith(
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

  void _showNameEditDialog(BuildContext context, bool isDark) {
    showDialog(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          backgroundColor: isDark ? AppColors.darkSurface : Colors.white,
          title: Text(
            'Ismni tahrirlash',
            style: AppTextStyles.headlineMedium.copyWith(
              color: isDark ? AppColors.darkText : AppColors.primary,
            ),
          ),
          content: TextField(
            controller: _nameController,
            style: AppTextStyles.bodyLarge.copyWith(
              color: isDark ? AppColors.darkText : AppColors.textPrimary,
            ),
            decoration: const InputDecoration(
              labelText: 'Ismingiz',
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: Text(
                'Bekor qilish',
                style: TextStyle(color: isDark ? AppColors.darkText2 : AppColors.textSecondary),
              ),
            ),
            ElevatedButton(
              onPressed: () {
                context.read<AuthBloc>().add(
                      UpdateProfileEvent(
                        name: _nameController.text.trim(),
                        language: _selectedLanguage,
                      ),
                    );
                Navigator.pop(ctx);
              },
              child: const Text('Saqlash'),
            ),
          ],
        );
      },
    );
  }

  void _showLanguageDialog(BuildContext context, bool isDark) {
    showDialog(
      context: context,
      builder: (ctx) {
        return AlertDialog(
          backgroundColor: isDark ? AppColors.darkSurface : Colors.white,
          title: Text(
            'Tilni tanlang',
            style: AppTextStyles.headlineMedium.copyWith(
              color: isDark ? AppColors.darkText : AppColors.primary,
            ),
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _buildLangTile(ctx, 'uz', 'O\'zbek (Lotin)'),
              _buildLangTile(ctx, 'uzk', 'Ўзбек (Кирил)'),
              _buildLangTile(ctx, 'ru', 'Русский'),
              _buildLangTile(ctx, 'en', 'English'),
            ],
          ),
        );
      },
    );
  }

  Widget _buildLangTile(BuildContext dialogContext, String code, String label) {
    final isSelected = _selectedLanguage == code;
    return ListTile(
      title: Text(
        label,
        style: AppTextStyles.labelLarge.copyWith(
          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
          color: isSelected ? AppColors.gold : null,
        ),
      ),
      trailing: isSelected ? const Icon(Icons.check_rounded, color: AppColors.gold) : null,
      onTap: () {
        setState(() {
          _selectedLanguage = code;
        });
        context.read<AuthBloc>().add(
              UpdateProfileEvent(
                name: _nameController.text.trim(),
                language: code,
              ),
            );
        Navigator.pop(dialogContext);
      },
    );
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }
}
