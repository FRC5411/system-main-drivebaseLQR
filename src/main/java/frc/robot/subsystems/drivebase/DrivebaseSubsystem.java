// ----------------------------------------------------------------[Package]----------------------------------------------------------------//
package frc.robot.subsystems.drivebase;
// ---------------------------------------------------------------[Libraries]---------------------------------------------------------------//

import com.pathplanner.lib.PathPlannerTrajectory;
import com.pathplanner.lib.auto.PIDConstants;
import com.pathplanner.lib.auto.SwerveAutoBuilder;
import com.pathplanner.lib.server.PathPlannerServer;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.SwerveModule;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static frc.robot.subsystems.drivebase.Constants.Hardware;
import static frc.robot.subsystems.drivebase.Constants.Values;

// -------------------------------------------------------[Drivebase Subsystem Class]-------------------------------------------------------//
//TODO Pathplanner Integration, Limelight Measurement Pose Estimation, Configurations
public final class DrivebaseSubsystem extends SubsystemBase implements AutoCloseable, Consumer<SwerveModuleState[]>, Supplier<Pose2d> {
  // --------------------------------------------------------------[Constants]--------------------------------------------------------------//
  private static DrivebaseSubsystem INSTANCE;

  private static final Stream<SwerveModule> MODULES = Stream.of(
    Hardware.Modules.FL_Module.Components.MODULE,
    Hardware.Modules.FR_Module.Components.MODULE,
    Hardware.Modules.RL_Module.Components.MODULE,
    Hardware.Modules.RR_Module.Components.MODULE).parallel();

  private static final SwerveDriveKinematics KINEMATICS = new SwerveDriveKinematics(
    new Translation2d((Values.Chassis.ROBOT_WIDTH)/(2), (Values.Chassis.ROBOT_WIDTH)/(2)),
    new Translation2d((Values.Chassis.ROBOT_WIDTH)/(2), -(Values.Chassis.ROBOT_WIDTH)/(2)),
    new Translation2d(-(Values.Chassis.ROBOT_WIDTH)/(2), (Values.Chassis.ROBOT_WIDTH)/(2)),
    new Translation2d(-(Values.Chassis.ROBOT_WIDTH)/(2), -(Values.Chassis.ROBOT_WIDTH)/(2)));

  private static final SwerveDrivePoseEstimator POSE_ESTIMATOR = new SwerveDrivePoseEstimator(
    KINEMATICS,
    Hardware.GYROSCOPE.getRotation2d(),
    getModulePositions(),
    new Pose2d()
  );

  private static final Field2d FIELD = new Field2d();

  // ---------------------------------------------------------------[Fields]----------------------------------------------------------------//
  private static Boolean FieldOriented = (false);  
  private static Double TimeInterval = (0.0);
  // ------------------------------------------------------------[Constructors]-------------------------------------------------------------//
  private DrivebaseSubsystem() {
    PathPlannerServer.startServer(Values.Port.PATHPLANNER_SERVER_PORT);
  }
  // ---------------------------------------------------------------[Methods]---------------------------------------------------------------//
  @Override
  public void periodic() {
    double IntervalTime;
    if(TimeInterval != (0)) {
      var RealTime = Timer.getFPGATimestamp();
      IntervalTime = RealTime - TimeInterval;
      TimeInterval = RealTime;
    } else {
      IntervalTime = (0.02);
    }
    FIELD.setRobotPose(POSE_ESTIMATOR.updateWithTime((IntervalTime), Hardware.GYROSCOPE.getRotation2d(),getModulePositions()));
    AtomicReference<Integer> ModuleNumber = new AtomicReference<>(1);
    MODULES.forEach(
    (Module) -> {
      Module.post(ModuleNumber.get()); 
      ModuleNumber.set(ModuleNumber.get() + (1));
    });
  }

  public void accept(final SwerveModuleState[] Demand) {
    set(Arrays.asList(Demand),() -> (false));
  }

  public synchronized void reset(Pose2d FieldRelativePose) {
    POSE_ESTIMATOR.resetPosition(
    Hardware.GYROSCOPE.getRotation2d(),    
    getModulePositions(),
    FieldRelativePose);
  }

  @SuppressWarnings("unused")
  public static synchronized void stop() {
    MODULES.forEach(SwerveModule::stop);
  }

  public void close() {
    MODULES.close();
      FIELD.close();
  }
  // --------------------------------------------------------------[Mutators]-----------------------------i9u87----------------------------------//
  @SuppressWarnings("unused")
  public static synchronized void set(final Double Translation_X, final Double Translation_Y, final Double Orientation, BooleanSupplier ControlType) {
    var Demand = (List.of(KINEMATICS.toSwerveModuleStates((FieldOriented) ?
            (ChassisSpeeds.fromFieldRelativeSpeeds(Translation_X, Translation_Y, Orientation, Hardware.GYROSCOPE.getRotation2d())) :
            (new ChassisSpeeds(Translation_X, Translation_Y, Orientation)))));
    Demand.forEach(
      (State) ->
        State.speedMetersPerSecond = (((State.speedMetersPerSecond * (60)) / Values.Chassis.WHEEL_DIAMETER) * Values.Chassis.DRIVETRAIN_GEAR_RATIO) * (Values.ComponentData.ENCODER_SENSITIVITY / (600)));
    set(Demand,ControlType);
  }

  @SuppressWarnings("all")
  public static synchronized void set(final List<SwerveModuleState> Demand, BooleanSupplier ControlType) {
    SwerveDriveKinematics.desaturateWheelSpeeds((SwerveModuleState[])Demand.toArray(),Values.Limit.ROBOT_MAXIMUM_VELOCITY);
    MODULES.forEach((Module) -> Module.set(() -> Demand.iterator().next(), ControlType));
  }

  @SuppressWarnings("all")
  public static synchronized void set(final List<SwerveModuleState> Demand) {
    SwerveDriveKinematics.desaturateWheelSpeeds((SwerveModuleState[])Demand.toArray(),Values.Limit.ROBOT_MAXIMUM_VELOCITY);
    MODULES.forEach((Module) -> Module.set(() -> Demand.iterator().next(), () -> (false)));
  }
  
  @SuppressWarnings("unused")
  public static synchronized void set() {
    set(List.of(
      new SwerveModuleState((0.0),new Rotation2d(Units.degreesToRadians((315)))),
      new SwerveModuleState((0.0),new Rotation2d(Units.degreesToRadians((45)))),
      new SwerveModuleState((0.0),new Rotation2d(Units.degreesToRadians((225)))),
      new SwerveModuleState((0.0),new Rotation2d(Units.degreesToRadians((135))))),
            () -> (false));
  }

  @SuppressWarnings("unused")
  public static void setFieldOriented(final Boolean isFieldOriented) {
    FieldOriented = isFieldOriented;
  }

  @SuppressWarnings("unused")
  public static void toggleFieldOriented() {
    FieldOriented = !FieldOriented;
  }
  // --------------------------------------------------------------[Accessors]--------------------------------------------------------------//
  @SuppressWarnings("unused")
  public static synchronized Command getAutonomousCommand(final PathPlannerTrajectory CompoundTrajectory, final HashMap<String,Command> EventMap, final Boolean IsBlue) {
    MODULES.forEach(SwerveModule::reset);
    return new SwerveAutoBuilder(
      INSTANCE,
      INSTANCE::reset,
      KINEMATICS,
      new PIDConstants(Constants.Values.PathPlanner.TRANSLATION_KP, Constants.Values.PathPlanner.TRANSLATION_KI, Constants.Values.PathPlanner.TRANSLATION_KD),
      new PIDConstants(Constants.Values.PathPlanner.ROTATION_KP, Constants.Values.PathPlanner.ROTATION_KI, Constants.Values.PathPlanner.ROTATION_KD), 
      INSTANCE, 
      (EventMap),
      (IsBlue),(INSTANCE)
    ).fullAuto(CompoundTrajectory);
  }

  public static SwerveModulePosition[] getModulePositions() {
    return (SwerveModulePosition[]) MODULES.map(
            (Module) ->
                    new SwerveModulePosition((Values.ComponentData.SCALE_FACTOR * (Module.getVelocity()) * Values.Chassis.DRIVETRAIN_GEAR_RATIO * Values.Chassis.WHEEL_PERIMETER), Module.getPosition())).toArray();
  }

  @SuppressWarnings("unused")
  public static SwerveModuleState[] getModuleStates() {
    return (SwerveModuleState[]) MODULES.map(SwerveModule::getModuleState).toArray();
  }

  public static synchronized DrivebaseSubsystem getInstance() {
    if(java.util.Objects.equals(INSTANCE, (null))) {
      INSTANCE = new DrivebaseSubsystem();
    }
    return INSTANCE;
  }

  public Pose2d get() {
    return POSE_ESTIMATOR.getEstimatedPosition();
  }
}