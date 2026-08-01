import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';
import '../../../../app/colors.dart';
import '../../../../app/text_styles.dart';
import '../../../../app/spacing.dart';
import '../bloc/auth_bloc.dart';
import '../bloc/auth_event.dart';
import '../bloc/auth_state.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  
  bool _obscurePassword = true;
  String _selectedLanguage = 'uz';

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    
    final inputBgColor = isDark ? AppColors.darkSurface2 : AppColors.background;
    final borderColor = isDark ? AppColors.darkBorder : AppColors.border;
    final activeBorderColor = isDark ? AppColors.primaryLight : AppColors.primary;

    return Scaffold(
      backgroundColor: isDark ? AppColors.darkBackground : AppColors.background,
      appBar: AppBar(
        title: const Text('Ro\'yxatdan o\'tish'),
        backgroundColor: Colors.transparent,
        foregroundColor: isDark ? AppColors.darkText : AppColors.textPrimary,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new_rounded),
          onPressed: () => context.pop(),
        ),
      ),
      body: BlocConsumer<AuthBloc, AuthState>(
        listener: (context, state) {
          if (state is AuthAuthenticated) {
            context.go('/');
          } else if (state is AuthError) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(state.message), 
                backgroundColor: AppColors.error,
                behavior: SnackBarBehavior.floating,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
            );
          }
        },
        builder: (context, state) {
          return Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl, vertical: AppSpacing.xl),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 400),
                child: Form(
                  key: _formKey,
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      // Muslim crescent illustration
                      Center(
                        child: Container(
                          padding: const EdgeInsets.all(AppSpacing.md),
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: AppColors.primaryLight.withOpacity(0.12),
                          ),
                          child: const Text(
                            '☪',
                            style: TextStyle(
                              fontSize: 40,
                              color: AppColors.gold,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: AppSpacing.lg),
                      
                      Text(
                        'Yangi hisob yaratish',
                        textAlign: TextAlign.center,
                        style: AppTextStyles.headlineLarge.copyWith(
                          color: isDark ? AppColors.darkText : AppColors.primary,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: AppSpacing.xs),
                      Text(
                        'Bizga qo\'shiling va Qur\'on ilmini o\'rganing',
                        textAlign: TextAlign.center,
                        style: AppTextStyles.bodyMedium.copyWith(
                          color: isDark ? AppColors.darkText2 : AppColors.textSecondary,
                        ),
                      ),
                      const SizedBox(height: AppSpacing.x3l),

                      // Name Field
                      TextFormField(
                        controller: _nameController,
                        style: AppTextStyles.bodyLarge.copyWith(
                          color: isDark ? AppColors.darkText : AppColors.textPrimary,
                        ),
                        decoration: InputDecoration(
                          labelText: 'Ismingiz',
                          labelStyle: AppTextStyles.labelMedium.copyWith(
                            color: isDark ? AppColors.darkText2 : AppColors.textSecondary,
                          ),
                          prefixIcon: Icon(Icons.person_outline_rounded, color: isDark ? AppColors.primaryLight : AppColors.primary),
                          filled: true,
                          fillColor: inputBgColor,
                          contentPadding: const EdgeInsets.symmetric(vertical: 18),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: borderColor),
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: borderColor),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: activeBorderColor, width: 2),
                          ),
                        ),
                        validator: (v) => v == null || v.isEmpty ? 'Ismingizni kiriting' : null,
                      ),
                      const SizedBox(height: AppSpacing.md),

                      // Email Field
                      TextFormField(
                        controller: _emailController,
                        keyboardType: TextInputType.emailAddress,
                        style: AppTextStyles.bodyLarge.copyWith(
                          color: isDark ? AppColors.darkText : AppColors.textPrimary,
                        ),
                        decoration: InputDecoration(
                          labelText: 'Email',
                          labelStyle: AppTextStyles.labelMedium.copyWith(
                            color: isDark ? AppColors.darkText2 : AppColors.textSecondary,
                          ),
                          prefixIcon: Icon(Icons.email_outlined, color: isDark ? AppColors.primaryLight : AppColors.primary),
                          filled: true,
                          fillColor: inputBgColor,
                          contentPadding: const EdgeInsets.symmetric(vertical: 18),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: borderColor),
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: borderColor),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: activeBorderColor, width: 2),
                          ),
                        ),
                        validator: (v) => v == null || !v.contains('@') ? 'To\'g\'ri email kiriting' : null,
                      ),
                      const SizedBox(height: AppSpacing.md),

                      // Password Field
                      TextFormField(
                        controller: _passwordController,
                        obscureText: _obscurePassword,
                        style: AppTextStyles.bodyLarge.copyWith(
                          color: isDark ? AppColors.darkText : AppColors.textPrimary,
                        ),
                        decoration: InputDecoration(
                          labelText: 'Parol',
                          labelStyle: AppTextStyles.labelMedium.copyWith(
                            color: isDark ? AppColors.darkText2 : AppColors.textSecondary,
                          ),
                          prefixIcon: Icon(Icons.lock_outline_rounded, color: isDark ? AppColors.primaryLight : AppColors.primary),
                          suffixIcon: IconButton(
                            icon: Icon(
                              _obscurePassword ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                              color: isDark ? AppColors.darkText2 : AppColors.textTertiary,
                            ),
                            onPressed: () {
                              setState(() {
                                _obscurePassword = !_obscurePassword;
                              });
                            },
                          ),
                          filled: true,
                          fillColor: inputBgColor,
                          contentPadding: const EdgeInsets.symmetric(vertical: 18),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: borderColor),
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: borderColor),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: activeBorderColor, width: 2),
                          ),
                        ),
                        validator: (v) => v == null || v.length < 8 ? 'Parol 8 belgidan kam bo\'lmasligi kerak' : null,
                      ),
                      const SizedBox(height: AppSpacing.md),

                      // Language Dropdown
                      DropdownButtonFormField<String>(
                        value: _selectedLanguage,
                        style: AppTextStyles.bodyLarge.copyWith(
                          color: isDark ? AppColors.darkText : AppColors.textPrimary,
                        ),
                        dropdownColor: isDark ? AppColors.darkSurface : Colors.white,
                        decoration: InputDecoration(
                          labelText: 'Til',
                          labelStyle: AppTextStyles.labelMedium.copyWith(
                            color: isDark ? AppColors.darkText2 : AppColors.textSecondary,
                          ),
                          prefixIcon: Icon(Icons.language_rounded, color: isDark ? AppColors.primaryLight : AppColors.primary),
                          filled: true,
                          fillColor: inputBgColor,
                          contentPadding: const EdgeInsets.symmetric(vertical: 16),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: borderColor),
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: borderColor),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: activeBorderColor, width: 2),
                          ),
                        ),
                        items: const [
                          DropdownMenuItem(value: 'uz', child: Text('O\'zbek (Lotin)')),
                          DropdownMenuItem(value: 'uzk', child: Text('Ўзбек (Кирил)')),
                          DropdownMenuItem(value: 'ru', child: Text('Русский')),
                          DropdownMenuItem(value: 'en', child: Text('English')),
                        ],
                        onChanged: (val) {
                          if (val != null) {
                            setState(() {
                              _selectedLanguage = val;
                            });
                          }
                        },
                      ),
                      const SizedBox(height: AppSpacing.xl),

                      // Submit Button (Gradient with custom shadow)
                      if (state is AuthLoading)
                        const Center(child: CircularProgressIndicator())
                      else
                        Container(
                          height: 52,
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(12),
                            gradient: const LinearGradient(
                              colors: [AppColors.primary, AppColors.primaryMid],
                              begin: Alignment.topLeft,
                              end: Alignment.bottomRight,
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: AppColors.primary.withOpacity(0.35),
                                blurRadius: 16,
                                offset: const Offset(0, 6),
                              ),
                            ],
                          ),
                          child: ElevatedButton(
                            onPressed: _submit,
                            style: ElevatedButton.styleFrom(
                              backgroundColor: Colors.transparent,
                              shadowColor: Colors.transparent,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                            child: Text(
                              'Ro\'yxatdan o\'tish',
                              style: AppTextStyles.labelLarge.copyWith(
                                color: Colors.white,
                                fontWeight: FontWeight.bold,
                                fontSize: 16,
                              ),
                            ),
                          ),
                        ),
                      const SizedBox(height: AppSpacing.xl),

                      // Link to login screen
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text(
                            'Hisobingiz bormi? ',
                            style: AppTextStyles.bodyMedium.copyWith(
                              color: isDark ? AppColors.darkText2 : AppColors.textSecondary,
                            ),
                          ),
                          GestureDetector(
                            onTap: () => context.pop(),
                            child: Text(
                              'Tizimga kiring',
                              style: AppTextStyles.labelLarge.copyWith(
                                color: isDark ? AppColors.primaryLight : AppColors.primary,
                                fontWeight: FontWeight.bold,
                              ),
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
        },
      ),
    );
  }

  void _submit() {
    if (_formKey.currentState?.validate() ?? false) {
      context.read<AuthBloc>().add(
            RegisterEvent(
              name: _nameController.text.trim(),
              email: _emailController.text.trim(),
              password: _passwordController.text,
              language: _selectedLanguage,
            ),
          );
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }
}
