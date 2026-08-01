import 'package:flutter/material.dart';

import '../app/colors.dart';
import '../app/text_styles.dart';

class GlowCircleButton extends StatelessWidget {
  const GlowCircleButton({
    super.key,
    required this.icon,
    required this.onPressed,
    this.size = 56,
  });

  final IconData icon;
  final VoidCallback onPressed;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onPressed,
        customBorder: const CircleBorder(),
        child: Container(
          width: size,
          height: size,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: const LinearGradient(
              colors: [AppColors.neonCyan, AppColors.neonTeal],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            boxShadow: [
              BoxShadow(
                color: AppColors.neonTeal.withOpacity(0.55),
                blurRadius: 22,
                spreadRadius: 1,
              ),
            ],
          ),
          child: Icon(icon, color: AppColors.midnightDeep, size: size * 0.42),
        ),
      ),
    );
  }
}

class GlowPrimaryButton extends StatelessWidget {
  const GlowPrimaryButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.expanded = true,
  });

  final String label;
  final VoidCallback onPressed;
  final bool expanded;

  @override
  Widget build(BuildContext context) {
    final button = Container(
      height: 52,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        gradient: const LinearGradient(
          colors: [AppColors.neonCyan, AppColors.neonTeal],
        ),
        boxShadow: [
          BoxShadow(
            color: AppColors.neonTeal.withOpacity(0.4),
            blurRadius: 20,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onPressed,
          borderRadius: BorderRadius.circular(16),
          child: Center(
            child: Text(
              label,
              style: AppTextStyles.labelLarge.copyWith(
                color: AppColors.midnightDeep,
                fontWeight: FontWeight.bold,
                fontSize: 16,
              ),
            ),
          ),
        ),
      ),
    );
    return expanded ? SizedBox(width: double.infinity, child: button) : button;
  }
}
