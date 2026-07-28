import 'package:flutter/material.dart';
import '../../../../app/colors.dart';

class MicButton extends StatefulWidget {
  final bool isRecording;
  final VoidCallback onTap;

  const MicButton({
    super.key,
    required this.isRecording,
    required this.onTap,
  });

  @override
  State<MicButton> createState() => _MicButtonState();
}

class _MicButtonState extends State<MicButton> with TickerProviderStateMixin {
  late AnimationController _pulseController;
  late AnimationController _scaleController;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    );

    _scaleController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 300),
      lowerBound: 0.92,
      upperBound: 1.0,
      value: 1.0,
    );

    if (widget.isRecording) {
      _pulseController.repeat();
    }
  }

  @override
  void didUpdateWidget(covariant MicButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isRecording && !oldWidget.isRecording) {
      _pulseController.repeat();
    } else if (!widget.isRecording && oldWidget.isRecording) {
      _pulseController.stop();
      _pulseController.reset();
    }
  }

  @override
  void dispose() {
    _pulseController.dispose();
    _scaleController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => _scaleController.animateTo(0.92, duration: const Duration(milliseconds: 100)),
      onTapUp: (_) {
        _scaleController.animateTo(1.0, duration: const Duration(milliseconds: 100));
        widget.onTap();
      },
      onTapCancel: () => _scaleController.animateTo(1.0, duration: const Duration(milliseconds: 100)),
      child: Center(
        child: ScaleTransition(
          scale: _scaleController,
          child: Stack(
            alignment: Alignment.center,
            children: [
              // Pulse Ring 2 (Outer)
              if (widget.isRecording)
                AnimatedBuilder(
                  animation: _pulseController,
                  builder: (context, child) {
                    final progress = (_pulseController.value + 0.27) % 1.0;
                    final scale = 1.0 + (progress * 1.2);
                    final opacity = (1.0 - progress).clamp(0.0, 1.0) * 0.35;
                    return Container(
                      height: 100,
                      width: 100,
                      transform: Matrix4.identity()
                        ..scale(scale)
                        ..translate(-50.0 * (scale - 1), -50.0 * (scale - 1)),
                      transformAlignment: Alignment.center,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: AppColors.primaryLight.withValues(alpha: opacity),
                      ),
                    );
                  },
                ),
              // Pulse Ring 1 (Inner)
              if (widget.isRecording)
                AnimatedBuilder(
                  animation: _pulseController,
                  builder: (context, child) {
                    final scale = 1.0 + (_pulseController.value * 0.8);
                    final opacity = (1.0 - _pulseController.value).clamp(0.0, 1.0) * 0.5;
                    return Container(
                      height: 100,
                      width: 100,
                      transform: Matrix4.identity()
                        ..scale(scale)
                        ..translate(-50.0 * (scale - 1), -50.0 * (scale - 1)),
                      transformAlignment: Alignment.center,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: AppColors.primaryLight.withValues(alpha: opacity),
                      ),
                    );
                  },
                ),
              // Main central button
              Container(
                height: 100,
                width: 100,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: widget.isRecording
                      ? AppColors.error.withValues(alpha: 0.15)
                      : AppColors.primaryLight.withValues(alpha: 0.12),
                ),
                alignment: Alignment.center,
                child: Container(
                  height: 80,
                  width: 80,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: LinearGradient(
                      colors: widget.isRecording
                          ? [AppColors.error, Colors.redAccent]
                          : [AppColors.primaryMid, AppColors.primary],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: (widget.isRecording ? AppColors.error : AppColors.primary)
                            .withValues(alpha: 0.45),
                        blurRadius: 20,
                        spreadRadius: 1,
                        offset: const Offset(0, 8),
                      )
                    ],
                  ),
                  child: Icon(
                    widget.isRecording ? Icons.stop_rounded : Icons.mic_rounded,
                    color: Colors.white,
                    size: 38,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
