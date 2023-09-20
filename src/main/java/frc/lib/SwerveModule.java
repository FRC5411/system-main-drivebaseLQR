// ----------------------------------------------------------------[Package]----------------------------------------------------------------//
package frc.lib;
// ---------------------------------------------------------------[Libraries]---------------------------------------------------------------//
import edu.wpi.first.wpilibj.motorcontrol.MotorControllerGroup;
import edu.wpi.first.wpilibj2.command.ParallelCommandGroup;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.math.system.LinearSystemLoop;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.geometry.Rotation2d;
import java.util.function.BooleanSupplier;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N2;
import edu.wpi.first.math.VecBuilder;
import java.util.function.Consumer;
import edu.wpi.first.wpilibj.Timer;
import java.util.function.Supplier;
import java.io.Closeable;

// -----------------------------------------------------------[Motion Module Class]---------------------------------------------------------//
/**
 * 
 * 
 * <h1> SwerveModule </h1>
 * 
 * <p> A state is made up of a three dimensional {@link edu.wpi.first.math.geometry.Translation3d Translation}, and a three dimensional 
 * <p> Represents an {@link  edu.wpi.first.math.system.LinearSystemLoop LinearSystemLoop} based approach to a individual swerve
 * module; a module that has full control over both the translation (velocity), and rotation(position) of it's wheel. Meaning that it
 * has the capability to move in any direction. </p>
 * 
 * <p> SwerveModules can consume a {@link edu.wpi.first.math.kinematics.SwerveModuleState SwerveModuleState} as a demand, and computes
 * the nesscessary voltage using a {@link  edu.wpi.first.math.system.LinearSystemLoop LinearSystemLoop} control loop. <p>
 * 
 * @author Cody Washington (Jelatinone)
 */
public final class SwerveModule implements Closeable, Consumer<SwerveModuleState> {
  // --------------------------------------------------------------[Constants]--------------------------------------------------------------//
  private final Supplier<TrapezoidProfile.Constraints> ROTATIONAL_MOTION_CONSTRAINTS;
  private final Supplier<Double> MAXIMUM_TRANSLATIONAL_VELOCITY;  
  private final LinearSystemLoop<N2,N1,N1> MOTION_CONTROL_LOOP;  
  private final MotorControllerGroup TRANSLATION_CONTROLLER;
  private final MotorControllerGroup ROTATION_CONTROLLER;   
  private final Supplier<SwerveModuleState> STATE_SENSOR; 

  // ---------------------------------------------------------------[Fields]----------------------------------------------------------------//
  private TrapezoidProfile.State TargetPositionStateReference = new TrapezoidProfile.State();   
  private TrapezoidProfile.State TargetPositionState = new TrapezoidProfile.State();   
  private ParallelCommandGroup TargetStateCommand = new ParallelCommandGroup();  
  private SwerveModuleState Demand = new SwerveModuleState();
  private Double TimeReference = (0.0);

  // ------------------------------------------------------------[Constructors]-------------------------------------------------------------//
  /**
   * Constructor.
   * @param TranslationController - The motor controller(s) responsible for controlling linear translation (velocity); querried with {@link #setVelocity(VelocitySupplier, isOpenLoop)}
   * @param RotationController  - The motor controller(s) responsible for controlling azimuth rotation (position); querried with {@link #setPosition(RotationSupplier)}
   * @param StateSensor - The sum all collected sensor(s) data into a single {@link edu.wpi.first.math.kinematics.SwerveModuleState SwerveModuleState} supplier
   * @param MaximumTranslationVelocity - The constraints supplier placed on the Translation Controller which determine maximum velocity output
   * @param RotationMotionConstraints - The constraint supplier placed on the RotationController which determine maximum velocity output, and maximum change in velocity in an instant time
   * @param MotionControlLoop - The control loop responsible for controller azimuth output
   */
  public SwerveModule(final MotorControllerGroup TranslationController, final MotorControllerGroup RotationController, final Supplier<SwerveModuleState> StateSensor,
    final Supplier<Double> MaximumTranslationVelocity, final Supplier<TrapezoidProfile.Constraints> RotationMotionConstraints,  LinearSystemLoop<N2,N1,N1> MotionControlLoop) {
    MAXIMUM_TRANSLATIONAL_VELOCITY = MaximumTranslationVelocity;      
    ROTATIONAL_MOTION_CONSTRAINTS = RotationMotionConstraints;     
    TRANSLATION_CONTROLLER = TranslationController;
    ROTATION_CONTROLLER = RotationController;
    MOTION_CONTROL_LOOP = MotionControlLoop;
    STATE_SENSOR = StateSensor;

  }
  /**
   * Constructor.
   * @param TranslationController - The motor controller(s) responsible for controlling linear translation (velocity); querried with {@link #setVelocity(VelocitySupplier, isOpenLoop)}
   * @param RotationController  - The motor controller(s) responsible for controlling azimuth rotation (position); querried with {@link #setPosition(RotationSupplier)}
   * @param StateSensor - The sum all collected sensor(s) data into a single {@link edu.wpi.first.math.kinematics.SwerveModuleState SwerveModuleState} supplier
   * @param MaximumTranslationVelocity - The constraints placed on the Translation Controller which determine maximum velocity output
   * @param RotationMotionConstraints - The constraint placed on the RotationController which determine maximum velocity output, and maximum change in velocity in an instant time
   * @param MotionControlLoop - The control loop responsible for controller azimuth output
   */
  public SwerveModule(final MotorControllerGroup TranslationController, final MotorControllerGroup RotationController, final Supplier<SwerveModuleState> StateSensor,
    final Double MaximumTranslationVelocity, final TrapezoidProfile.Constraints RotationMotionConstraints,  LinearSystemLoop<N2,N1,N1> MotionControlLoop)  {
    MAXIMUM_TRANSLATIONAL_VELOCITY = () -> MaximumTranslationVelocity;      
    ROTATIONAL_MOTION_CONSTRAINTS =  () -> RotationMotionConstraints;     
    TRANSLATION_CONTROLLER = TranslationController;
    ROTATION_CONTROLLER = RotationController;
    MOTION_CONTROL_LOOP = MotionControlLoop;
    STATE_SENSOR = StateSensor;

  }
  // --------------------------------------------------------------[Mutators]---------------------------------------------------------------//
  /**
   * Set the translation (linear velocity) controller to meet a specified demand using a control type
   * @param Demand - The specified demand as a translation in two dimensional space in meters / second
   * @param ControlType - The type of control of meeting the demand, whether or not it is open loop.
   */
  public synchronized void setVelocity(final Supplier<Double> Demand, final Supplier<Boolean> ControlType) {
    var TranslationDemand = Demand.get();
    if(TranslationDemand != null & TranslationDemand != Double.NaN) {
      if(ControlType.get()) {
        TRANSLATION_CONTROLLER.set(TranslationDemand / MAXIMUM_TRANSLATIONAL_VELOCITY.get());
      } else {
        TRANSLATION_CONTROLLER.set(Demand.get());
      }      
    }
  }

  /**
   * Set the rotation (position velocity) controller to meet a specified demand using a control type
   * @param Demand - The specified demand as a rotation in two dimensional space as a {@link edu.wpi.first.math.geometry.Rotation2d Rotation2d}
   */
  public synchronized void setPosition(final Supplier<Rotation2d> Demand) {
    Rotation2d RotationDemand = Demand.get();
    if(RotationDemand != null & RotationDemand.getRadians() != Double.NaN) {
      Double IntervalTime;
      if(TimeReference != (0)) {
        var RealTime = Timer.getFPGATimestamp();
        IntervalTime = RealTime - TimeReference;
        TimeReference = RealTime;
      } else {
        IntervalTime = (0.02);
      }
      TrapezoidProfile.Constraints SystemConstraints = ROTATIONAL_MOTION_CONSTRAINTS.get();
      TargetPositionState = new TrapezoidProfile.State(RotationDemand.getRadians(), (0));
      TargetPositionStateReference = new TrapezoidProfile(SystemConstraints,TargetPositionState,TargetPositionStateReference).calculate(IntervalTime);
      MOTION_CONTROL_LOOP.setNextR(TargetPositionStateReference.position,TargetPositionStateReference.velocity);
      MOTION_CONTROL_LOOP.correct(VecBuilder.fill(STATE_SENSOR.get().angle.getRadians()));
      MOTION_CONTROL_LOOP.predict(IntervalTime);
      ROTATION_CONTROLLER.setVoltage(MOTION_CONTROL_LOOP.getU((0)));
    }
  }

  /**
   * Set the controllers to meet rotation, and translation demans packaged as a {@link edu.wpi.first.math.kinematics.SwerveModuleState SwerveModuleState}
   * @param Demand - The specified demand as a {@link frc.lib.MotionState MotionState} that has translation and rotation in three-dimensional space
   * @param ControlType - The type of control of meeting the velocity demand, whether or not it is open loop.
   */
  public synchronized void set(final Supplier<MotionState> Demand, final Supplier<Boolean> ControlType) {
    var DemandState = Demand.get();
    set(() -> new SwerveModuleState(new Translation2d(DemandState.TRANSLATION.getX(), DemandState.TRANSLATION.getY()).getNorm(),
              new Rotation2d(Demand.get().ROTATION.getX(), Demand.get().ROTATION.getY())), () -> ControlType.get());
  } 
  /**
   * Set the controllers to meet rotation, and translation demans packaged as a {@link edu.wpi.first.math.kinematics.SwerveModuleState SwerveModuleState}
   * @param Demand - The specified demand as a {@link edu.wpi.first.math.kinematics.SwerveModuleState SwerveModuleState}, or translation (velocity) as a {@link java.lang.Double Double}},
   *  and Rotation as radians in a {@link edu.wpi.first.math.geometry.Rotation2d Rotation2d}
   * @param ControlType - The type of control of meeting the velocity demand, whether or not it is open loop.
   */
  public synchronized void set(final Supplier<SwerveModuleState> Demand, final BooleanSupplier ControlType) {
    Supplier<SwerveModuleState> OptimizedDemand = () -> SwerveModuleState.optimize(Demand.get(), getPosition());
    TargetStateCommand.cancel();
    TargetStateCommand = new ParallelCommandGroup(
      new InstantCommand(() -> 
        setPosition(() -> new Rotation2d(OptimizedDemand.get().angle.getRadians()))
      ),
      new InstantCommand(() -> {
        SwerveModuleState DemandState = OptimizedDemand.get();
        setVelocity(() -> DemandState.speedMetersPerSecond, () ->  ControlType.getAsBoolean());
      }));
    TargetStateCommand.repeatedly().schedule();
  }
  // ---------------------------------------------------------------[Methods]---------------------------------------------------------------//
  /**
   * Reset the control loop system of the module, sets reference r and output u to zero
   */
  public synchronized void reset() {
    var State = STATE_SENSOR.get();
    var EncoderPositionRadian = State.angle.getRadians();
    var EncoderPositionRadiansPerSecond = State.angle.getRadians() / (TimeReference - Timer.getFPGATimestamp());
    MOTION_CONTROL_LOOP.reset(VecBuilder.fill(EncoderPositionRadian,EncoderPositionRadiansPerSecond));
    TargetPositionStateReference = new TrapezoidProfile.State(EncoderPositionRadian, EncoderPositionRadiansPerSecond);

  }

  /**
   * Close the held resources within the module, module is no longer usable after this operation.
   */
  public synchronized void close() {
    TRANSLATION_CONTROLLER.close();
    ROTATION_CONTROLLER.close();
    TargetStateCommand.cancel();
  }

  /**
   * Stop all of the controllers within the module immediately, and cancel all subsequent motions. make another call to {@link #set(SwerveStateSupplier, ControlType)} to call again.
   */
  public void stop() {
    TargetStateCommand.cancel();
    TRANSLATION_CONTROLLER.stopMotor();
    ROTATION_CONTROLLER.stopMotor();
  }  

  /**
   * Consume a SwerveModuleState as shorthand for calling {@link #set(SwerveStateSupplier, ControlType)}
   * @param Demand - The 
   */
  public void accept(final SwerveModuleState Demand) {
    set(STATE_SENSOR, () -> false);
  }

  /**
   * Post measurement, output, and demand data to shuffleboard static  instance
   */
  public void post(final Integer ModuleNumber) {
    var Prefix = ("MODULE [") + ModuleNumber + ("] ");
    SmartDashboard.putNumber(Prefix + "DEMAND ROTATION (Rad.)", Demand.angle.getRadians());
    SmartDashboard.putNumber(Prefix + "DEMAND VELOCITY (m/s)",Demand.speedMetersPerSecond);
    SmartDashboard.putNumber(Prefix + "MEASURED ROTATION (Rad.)",getPosition().getRadians());
    SmartDashboard.putNumber(Prefix + "MEASURED VELOCITY (m/s)", getVelocity());
    SmartDashboard.putNumber(Prefix + "OUTPUT ROTATION [-1,1]",ROTATION_CONTROLLER.get());
    SmartDashboard.putNumber(Prefix + "OUTPUT VELOCITY [-1,1]",TRANSLATION_CONTROLLER.get());
  }
  // --------------------------------------------------------------[Accessors]--------------------------------------------------------------//
  /**
   * Get the current state of the module as a {@link edu.wpi.first.math.kinematics.SwerveModuleState SwerveModuleState}.
   * @return A {@link edu.wpi.first.math.kinematics.SwerveModuleState SwerveModuleState} representation of the module's motion
   */
  public SwerveModuleState getModuleState() {
    return STATE_SENSOR.get();
  }

  /**
   * Get the current position (rotation) in radians as a {@link edu.wpi.first.math.geometry.Rotation2d Rotation2d}
   * @return A quantatative representation of the angle of the module
   */
  public Rotation2d getPosition() {
    return STATE_SENSOR.get().angle;
  }

  /**
   * Get the current velocity (translation) in m/s as a {@link java.lang.Double Double}
   * @return A quantatative representation of the angle of the module
   */
  public Double getVelocity() {
    return STATE_SENSOR.get().speedMetersPerSecond;
  }
}