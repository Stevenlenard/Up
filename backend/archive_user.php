<?php
header("Content-Type: application/json");
require_once 'db_config.php';

$data = json_decode(file_get_contents("php://input"));

if (!$data || !isset($data->user_id) || !isset($data->role) || !isset($data->is_archived)) {
    echo json_encode(["success" => false, "message" => "Invalid parameters"]);
    exit;
}

$user_id = $data->user_id;
$role = $data->role;
$is_archived = $data->is_archived ? 1 : 0;

try {
    if ($role === 'resident') {
        $query = "UPDATE residents SET is_archived = ? WHERE resident_id = ?";
    } else {
        $query = "UPDATE users SET is_archived = ? WHERE user_id = ?";
    }

    $stmt = $conn->prepare($query);
    $stmt->execute([$is_archived, $user_id]);

    if ($stmt->rowCount() > 0) {
        $message = $is_archived ? "User archived successfully" : "User unarchived successfully";
        echo json_encode(["success" => true, "message" => $message]);
    } else {
        echo json_encode(["success" => false, "message" => "User not found or no change made"]);
    }

} catch (PDOException $e) {
    echo json_encode(["success" => false, "message" => "Database Error: " . $e->getMessage()]);
}
?>
