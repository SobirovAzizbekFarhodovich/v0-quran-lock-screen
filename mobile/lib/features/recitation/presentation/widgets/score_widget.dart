import 'package:flutter/material.dart';
import '../../../../app/colors.dart';
import '../../../../app/text_styles.dart';

class ScoreWidget extends StatefulWidget {
  final double score;
  final int errorCount;
  final int wordCount;

  const ScoreWidget({
    super.key,
    required this.score,
    required this.errorCount,
    required this.wordCount,
  });

  @override
  State<ScoreWidget> createState() => _ScoreWidgetState();
}

class _ScoreWidgetState extends State<ScoreWidget> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );
    _animation = Tween<double>(begin: 0.0, end: widget.score).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOutExpo),
    );
    _controller.forward();
  }

  @override
  void didUpdateWidget(covariant ScoreWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.score != oldWidget.score) {
      _animation = Tween<double>(begin: oldWidget.score, end: widget.score).animate(
        CurvedAnimation(parent: _controller, curve: Curves.easeOutExpo),
      );
      _controller.reset();
      _controller.forward();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardBg = isDark ? AppColors.darkSurface : Colors.white;
    final shadowColor = isDark ? Colors.transparent : Colors.black.withValues(alpha: 0.04);
    final borderColor = isDark ? AppColors.darkBorder : AppColors.border;

    return Container(
      decoration: BoxDecoration(
        color: cardBg,
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
      padding: const EdgeInsets.all(20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Score Number (Animated count)
          AnimatedBuilder(
            animation: _animation,
            builder: (context, child) {
              return Text(
                _animation.value.toStringAsFixed(1),
                style: AppTextStyles.displayLarge.copyWith(
                  fontSize: 56,
                  color: isDark ? AppColors.primaryLight : AppColors.primary,
                  fontWeight: FontWeight.bold,
                ),
              );
            },
          ),
          // Subtitle
          Text(
            'Tajweed bali',
            style: AppTextStyles.labelSmall.copyWith(
              color: isDark ? AppColors.darkText2 : AppColors.textTertiary,
              fontSize: 11,
              fontWeight: FontWeight.w300,
            ),
          ),
          const SizedBox(height: 16),
          
          // Progress bar (Yashil)
          AnimatedBuilder(
            animation: _animation,
            builder: (context, child) {
              final double percent = (_animation.value / 100.0).clamp(0.0, 1.0);
              return Column(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: LinearProgressIndicator(
                      value: percent,
                      minHeight: 8,
                      backgroundColor: isDark ? AppColors.darkBorder : Colors.grey.shade200,
                      valueColor: AlwaysStoppedAnimation<Color>(
                        widget.score >= 90.0
                            ? AppColors.success
                            : (widget.score >= 80.0 ? AppColors.warning : AppColors.error),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    '${(_animation.value).toStringAsFixed(1)}%',
                    style: AppTextStyles.labelMedium.copyWith(
                      color: isDark ? AppColors.darkText : AppColors.textPrimary,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              );
            },
          ),
          const SizedBox(height: 16),

          // Status Chips: [2 xato 🔴]  [42 so'z ✓]
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Error chip
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: widget.errorCount > 0
                      ? AppColors.error.withValues(alpha: 0.1)
                      : AppColors.success.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: widget.errorCount > 0
                        ? AppColors.error.withValues(alpha: 0.3)
                        : AppColors.success.withValues(alpha: 0.3),
                  ),
                ),
                child: Row(
                  children: [
                    Text(
                      '${widget.errorCount} xato',
                      style: AppTextStyles.labelSmall.copyWith(
                        color: widget.errorCount > 0 ? AppColors.error : AppColors.success,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(width: 4),
                    Text(widget.errorCount > 0 ? '🔴' : '✓'),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              // Word chip
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: AppColors.success.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: AppColors.success.withValues(alpha: 0.3)),
                ),
                child: Row(
                  children: [
                    Text(
                      '${widget.wordCount} so\'z',
                      style: AppTextStyles.labelSmall.copyWith(
                        color: AppColors.success,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(width: 4),
                    const Text('✓'),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
